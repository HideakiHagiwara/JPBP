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

import org.compiere.model.MClient;
import org.compiere.model.MRMALine;
import org.compiere.model.MSysConfig;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.Msg;

public class JPiereRMALineModelValidator implements ModelValidator {


	private int AD_Client_ID = -1;


	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		if(client != null)
			this.AD_Client_ID = client.getAD_Client_ID();
		engine.addModelChange(MRMALine.Table_Name, this);

	}

	@Override
	public int getAD_Client_ID() {
		return AD_Client_ID;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {

		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception
	{

		//JPIERE-0375:Check Over Qty Invoiced
		if(type == ModelValidator.TYPE_BEFORE_CHANGE && po.is_ValueChanged("QtyInvoiced") )
		{
			MRMALine rmaLine = (MRMALine)po;
			if ( (rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYINVOICED_C-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
					  ||
					 (!rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYINVOICED_V-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
			    )
			{
				BigDecimal qtyOrdered = rmaLine.getQty();
				BigDecimal qtyInvoiced  = rmaLine.getQtyInvoiced();

				if(qtyOrdered.signum() >= 0)
				{
					if(qtyInvoiced.compareTo(qtyOrdered) > 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyInvoiced") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}

				}else {

					if(qtyInvoiced.compareTo(qtyOrdered) < 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyInvoiced") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}
				}
			}

		}//JPiere-0375


		//JPIERE-0376:Check Over Qty Delivered
		if(type == ModelValidator.TYPE_BEFORE_CHANGE && po.is_ValueChanged("QtyDelivered") )
		{
			MRMALine rmaLine = (MRMALine)po;
			if ( (rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYDELIVERED_C-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
					  ||
					 (!rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYDELIVERED_V-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
			    )
			{
				BigDecimal qtyOrdered = rmaLine.getQty();
				BigDecimal qtyDelivered  = rmaLine.getQtyDelivered();

				if(qtyOrdered.signum() >= 0)
				{
					if(qtyDelivered.compareTo(qtyOrdered) > 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyDelivered") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}

				}else {

					if(qtyDelivered.compareTo(qtyOrdered) < 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyDelivered") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}
				}
			}

		}//JPiere-0376


		//JPIERE-0377:Check Over Qty Recognized
		if(type == ModelValidator.TYPE_BEFORE_CHANGE && po.is_ValueChanged("JP_QtyRecognized") )
		{
			MRMALine rmaLine = (MRMALine)po;
			if ( (rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYRECOGNIZED_C-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
					  ||
				  (!rmaLine.getParent().isSOTrx() && MSysConfig.getBooleanValue("JP_CHECK_ORVER_QTYRECOGNIZED_V-RMA", false, rmaLine.getAD_Client_ID(), rmaLine.getAD_Org_ID()) )
			    )
			{
				BigDecimal qtyOrdered = rmaLine.getQty();
				BigDecimal qtyRecognized  = (BigDecimal)rmaLine.get_Value("JP_QtyRecognized");

				if(qtyOrdered.signum() >= 0)
				{
					if(qtyRecognized.compareTo(qtyOrdered) > 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyRecognized") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}

				}else {

					if(qtyRecognized.compareTo(qtyOrdered) < 0)
					{
						return Msg.getMsg(po.getCtx(), "JP_Over_QtyRecognized") + " : "+ rmaLine.getParent().getDocumentNo() +  " - " + rmaLine.getLine();
					}
				}
			}

		}//JPiere-0377


		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {

		return null;
	}


}