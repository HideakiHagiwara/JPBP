/******************************************************************************
 * Product: JPiere                                                            *
 * Copyright (C) Hideaki Hagiwara (h.hagiwara@oss-erp.co.jp)                  *
 *                                                                            *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY.                          *
 * See the GNU General Public License for more details.                       *
 *                                                                            *
 * JPiere is maintained by OSS ERP Solutions Co., Ltd.                        *
 * (http://www.oss-erp.co.jp)                                                 *
 *****************************************************************************/
package jpiere.base.plugin.org.adempiere.base;


import java.math.BigDecimal;
import java.sql.Timestamp;

import jpiere.base.plugin.org.adempiere.model.MDeliveryDays;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MClient;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrder;
import org.compiere.model.MPayment;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;

public class JPiereInOutModelValidator implements ModelValidator {

	private static CLogger log = CLogger.getCLogger(JPiereInOutModelValidator.class);
	private int AD_Client_ID = -1;
	private int AD_Org_ID = -1;
	private int AD_Role_ID = -1;
	private int AD_User_ID = -1;

	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		if(client != null)
			this.AD_Client_ID = client.getAD_Client_ID();
		engine.addDocValidate(MInOut.Table_Name, this);

	}

	@Override
	public int getAD_Client_ID() {
		return AD_Client_ID;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		this.AD_Org_ID = AD_Org_ID;
		this.AD_Role_ID = AD_Role_ID;
		this.AD_User_ID = AD_User_ID;

		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception
	{

		return null;
	}

	@Override
	public String docValidate(PO po, int timing)
	{
		//JPIERE-0219:Create Invoice When Ship/Receipt
		if(timing == ModelValidator.TIMING_AFTER_COMPLETE)
		{
			MInOut io = (MInOut)po;
			String trxName = po.get_TrxName();
			boolean isReversal = io.isReversal();
			MDocType ioDocType = MDocType.get(po.getCtx(), io.getC_DocType_ID());
			if(ioDocType.get_ValueAsBoolean("IsCreateInvoiceJP"))
			{
				if(io.getC_Order_ID()==0)
					return null;

				MOrder order = new MOrder(po.getCtx(), io.getC_Order_ID(), trxName);
				MDocType orderDocType = MDocType.get(po.getCtx(), order.getC_DocTypeTarget_ID());
				if(orderDocType.equals(MOrder.DocSubTypeSO_OnCredit)
						|| orderDocType.equals(MOrder.DocSubTypeSO_POS)
						|| orderDocType.equals(MOrder.DocSubTypeSO_Prepay))
				{
					return null;
				}

				if(orderDocType.getC_DocTypeInvoice_ID() == 0)
					return null;


				if(!isReversal && ioDocType.get_ValueAsBoolean("IsInspectionInvoiceJP"))
				{
					Timestamp invoiceDate = MDeliveryDays.getInvoiceDate(io, ioDocType.get_ValueAsBoolean("IsHolidayNotInspectionJP"));
					io.setDateAcct(invoiceDate);
					io.saveEx(trxName);
				}

				MInvoice invoice = new MInvoice (order, orderDocType.getC_DocTypeInvoice_ID(), io.getDateAcct());
				if (!invoice.save(trxName))
				{
					log.warning("Could not create Invoice: "+ io.getDocumentInfo());
					return null;
				}

				MInOutLine[] sLines = io.getLines(false);
				for (int i = 0; i < sLines.length; i++)
				{
					MInOutLine sLine = sLines[i];
					//
					MInvoiceLine iLine = new MInvoiceLine(invoice);
					iLine.setShipLine(sLine);
					//	Qty = Delivered
					if (sLine.sameOrderLineUOM())
						iLine.setQtyEntered(sLine.getQtyEntered());
					else
						iLine.setQtyEntered(sLine.getMovementQty());
					iLine.setQtyInvoiced(sLine.getMovementQty());
					if (!iLine.save(io.get_TrxName()))
					{
						log.warning("Could not create Invoice Line from Shipment Line: "+ invoice.getDocumentInfo());
						return null;
					}
					//
					sLine.setIsInvoiced(true);
					if (!sLine.save(trxName))
					{
						log.warning("Could not update Shipment line: " + sLine);
					}
				}//for

				if (!invoice.processIt(DocAction.ACTION_Complete))
					throw new AdempiereException("Failed when processing document - " + invoice.getProcessMsg());

				invoice.saveEx(trxName);
				if (!invoice.getDocStatus().equals(DocAction.STATUS_Completed))
				{
					log.warning("Could not Completed Invoice: "+ invoice.getDocumentInfo());
					return null;
				}

				//Allocation
				if(!isReversal && order.getC_Payment_ID() > 0)
				{

					MPayment payment = new MPayment(io.getCtx(),order.getC_Payment_ID(), trxName);
					if(!payment.isAllocated() && payment.getC_Order_ID()== order.getC_Order_ID() && payment.isPrepayment()
							&& (payment.getDocStatus().equals(DocAction.STATUS_Completed) || payment.getDocStatus().equals(DocAction.STATUS_Closed))
							&& (invoice.getC_Currency_ID() == payment.getC_Currency_ID()) )
					{
						BigDecimal payAmt = payment.getPayAmt();
						BigDecimal allocatedAmt = payment.getAllocatedAmt();
						BigDecimal allocatAmt = payAmt;
						if(allocatedAmt == null)
							allocatedAmt = Env.ZERO;

						if(payment.isReceipt()){
							allocatAmt = payAmt.subtract(allocatedAmt);
							allocatAmt = invoice.getGrandTotal().compareTo(allocatAmt) > 0 ? allocatAmt : invoice.getGrandTotal();
						}else if(!payment.isReceipt()){
							allocatAmt = payAmt.add(allocatedAmt);
							allocatAmt = invoice.getGrandTotal().compareTo(allocatAmt) > 0 ? allocatAmt : invoice.getGrandTotal();
							allocatAmt = allocatAmt.negate();
						}

						if((payment.isReceipt() && allocatAmt.compareTo(Env.ZERO) > 0)
								|| (!payment.isReceipt() && allocatAmt.compareTo(Env.ZERO) < 0) )
						{
							MAllocationHdr alloc = new MAllocationHdr(io.getCtx(), false, invoice.getDateAcct(), invoice.getC_Currency_ID(),
										Msg.translate(io.getCtx(), "C_Payment_ID")	+ ": " + payment.getDocumentNo(), trxName);
							alloc.setAD_Org_ID(invoice.getAD_Org_ID());
							alloc.setDateAcct(invoice.getDateAcct()); // in case date acct is different from datetrx in payment; IDEMPIERE-1532 tbayen
							if (!alloc.save(trxName))
							{
								log.severe("Allocations not created");
								return null;
							}

							MAllocationLine aLine = new MAllocationLine (alloc, allocatAmt, Env.ZERO, Env.ZERO, Env.ZERO);
							aLine.setDocInfo(invoice.getC_BPartner_ID(), order.getC_Order_ID(), invoice.getC_Invoice_ID());
							aLine.setPaymentInfo(payment.getC_Payment_ID(), 0);
							if (!aLine.save(trxName))
								log.warning("P.Allocations - line not saved");

							if (!alloc.processIt(DocAction.ACTION_Complete))
								throw new AdempiereException("Failed when processing document - " + alloc.getProcessMsg());
							if (!alloc.save(trxName))
							{
								log.severe("Allocation not Save after Complete");
								return null;
							}
						}
					}

				}//Allocation


			}//if(ioDocType.get_ValueAsBoolean("IsCreateInvoiceJP"))
		}

		return null;
	}

}