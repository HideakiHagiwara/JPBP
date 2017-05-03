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
package jpiere.base.plugin.org.adempiere.callout;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MDocType;
import org.compiere.model.MLocator;
import org.compiere.model.MOrder;
import org.compiere.model.X_C_Order;
import org.compiere.util.DB;
import org.compiere.util.Env;

/**
 *
 *  JPiere Estimation Document CallOut
 *
 *  JPIERE-0183:JPBP
 *
 * @author Hideaki Hagiwara
 *
 */
public class JPiereEstimationCallout implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue)
	{

		if(mField.getColumnName().equals("C_DocTypeTarget_ID"))
		{
			Integer C_DocTypeTarget_ID = (Integer)value;
			mTab.setValue ("C_DocType_ID", C_DocTypeTarget_ID);
		}


		if(mField.getColumnName().equals("JP_DocTypeSO_ID"))
		{
			Integer JP_DocTypeSO_ID = (Integer)value;		//	Actually JP_DocTypeSO_ID is JP_Estimtion Table
			if (JP_DocTypeSO_ID == null || JP_DocTypeSO_ID.intValue() == 0)
			{
				Env.setContext(ctx, WindowNo, "OrderType", MDocType.DOCSUBTYPESO_StandardOrder);
				mTab.setValue ("OrderType",  MDocType.DOCSUBTYPESO_StandardOrder);
				return "";
			}

			MDocType docTypeSO = MDocType.get(ctx, JP_DocTypeSO_ID);
			String DocSubTypeSO = docTypeSO.getDocSubTypeSO();
			if (DocSubTypeSO == null)
				DocSubTypeSO = "--";
			Env.setContext(ctx, WindowNo, "OrderType", DocSubTypeSO);
			mTab.setValue ("OrderType", DocSubTypeSO);


			//	Re-Create new DocNo, if there is a doc number already
			//	and the existing source used a different Sequence number
			String oldDocNo = (String)mTab.getValue("DocumentNo");
			boolean newDocNo = (oldDocNo == null);
			if (!newDocNo && oldDocNo.startsWith("<") && oldDocNo.endsWith(">"))
				newDocNo = true;
			Integer oldC_DocType_ID = (Integer)mTab.getValue("C_DocType_ID");

			String sql = "SELECT d.DocSubTypeSO,d.HasCharges,"			//	1..2
				+ "d.IsDocNoControlled,"     //  3
				+ "s.AD_Sequence_ID,d.IsSOTrx "                             //	4..5
				+ "FROM C_DocType d "
				+ "LEFT OUTER JOIN AD_Sequence s ON (d.DocNoSequence_ID=s.AD_Sequence_ID) "
				+ "WHERE C_DocType_ID=?";	//	#1
			PreparedStatement pstmt = null;
			ResultSet rs = null;
			try
			{
				int oldAD_Sequence_ID = 0;

				//	Get old AD_SeqNo for comparison
				if (!newDocNo && oldC_DocType_ID.intValue() != 0)
				{
					pstmt = DB.prepareStatement(sql, null);
					pstmt.setInt(1, oldC_DocType_ID.intValue());
					rs = pstmt.executeQuery();
					if (rs.next())
						oldAD_Sequence_ID = rs.getInt("AD_Sequence_ID");
					DB.close(rs, pstmt);
					rs = null;
					pstmt = null;
				}

				pstmt = DB.prepareStatement(sql, null);
				pstmt.setInt(1, JP_DocTypeSO_ID.intValue());
				rs = pstmt.executeQuery();

				boolean IsSOTrx = true;
				if (rs.next())		//	we found document type
				{
					//	No Drop Ship other than Standard
					if (!DocSubTypeSO.equals(MOrder.DocSubTypeSO_Standard))
						mTab.setValue ("IsDropShip", "N");

					//	Delivery Rule
					if (DocSubTypeSO.equals(MOrder.DocSubTypeSO_POS))
						mTab.setValue ("DeliveryRule", X_C_Order.DELIVERYRULE_Force);
					else if (DocSubTypeSO.equals(MOrder.DocSubTypeSO_Prepay))
						mTab.setValue ("DeliveryRule", X_C_Order.DELIVERYRULE_AfterReceipt);
					else
						mTab.setValue ("DeliveryRule", X_C_Order.DELIVERYRULE_Availability);

					//	Invoice Rule
					if (DocSubTypeSO.equals(MOrder.DocSubTypeSO_POS)
						|| DocSubTypeSO.equals(MOrder.DocSubTypeSO_Prepay)
						|| DocSubTypeSO.equals(MOrder.DocSubTypeSO_OnCredit) )
						mTab.setValue ("InvoiceRule", X_C_Order.INVOICERULE_Immediate);
					else
						mTab.setValue ("InvoiceRule", X_C_Order.INVOICERULE_AfterDelivery);

					//	Payment Rule - POS Order
					if (DocSubTypeSO.equals(MOrder.DocSubTypeSO_POS))
						mTab.setValue("PaymentRule", X_C_Order.PAYMENTRULE_Cash);
					else
						mTab.setValue("PaymentRule", X_C_Order.PAYMENTRULE_OnCredit);

					//	IsSOTrx
					if ("N".equals(rs.getString("IsSOTrx")))
						IsSOTrx = false;

					//	Set Context:
					Env.setContext(ctx, WindowNo, "HasCharges", rs.getString("HasCharges"));

					//	DocumentNo
	//				if (rs.getString("IsDocNoControlled").equals("Y"))			//	IsDocNoControlled
	//				{
	//					if (!newDocNo && oldAD_Sequence_ID != rs.getInt("AD_Sequence_ID"))
	//						newDocNo = true;
	//					if (newDocNo) {
	//						int AD_Sequence_ID = rs.getInt("AD_Sequence_ID");
	//						mTab.setValue("DocumentNo", MSequence.getPreliminaryNo(mTab, AD_Sequence_ID));
	//					}
	//				}
				}

				DB.close(rs, pstmt);
				rs = null;
				pstmt = null;

				//  When BPartner is changed, the Rules are not set if
				//  it is a POS or Credit Order (i.e. defaults from Standard BPartner)
				//  This re-reads the Rules and applies them.
				if (DocSubTypeSO.equals(MOrder.DocSubTypeSO_POS)
					|| DocSubTypeSO.equals(MOrder.DocSubTypeSO_Prepay))    //  not for POS/PrePay
					;
				else
				{
					sql = "SELECT PaymentRule,C_PaymentTerm_ID,"            //  1..2
						+ "InvoiceRule,DeliveryRule,"                       //  3..4
						+ "FreightCostRule,DeliveryViaRule, "               //  5..6
						+ "PaymentRulePO,PO_PaymentTerm_ID "
						+ "FROM C_BPartner "
						+ "WHERE C_BPartner_ID=?";		//	#1
					pstmt = DB.prepareStatement(sql, null);
					int C_BPartner_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_ID");
					pstmt.setInt(1, C_BPartner_ID);
					//
					rs = pstmt.executeQuery();
					if (rs.next())
					{
						//	PaymentRule
						String s = rs.getString(IsSOTrx ? "PaymentRule" : "PaymentRulePO");
						if (s != null && s.length() != 0)
						{
							if (IsSOTrx && (s.equals("B") || s.equals("S") || s.equals("U")))	//	No Cash/Check/Transfer for SO_Trx
								s = "P";										//  Payment Term
							if (!IsSOTrx && (s.equals("B")))					//	No Cash for PO_Trx
								s = "P";										//  Payment Term
							mTab.setValue("PaymentRule", s);
						}
						//	Payment Term
						Integer ii =new Integer(rs.getInt(IsSOTrx ? "C_PaymentTerm_ID" : "PO_PaymentTerm_ID"));
						if (!rs.wasNull())
							mTab.setValue("C_PaymentTerm_ID", ii);
						//	InvoiceRule
						s = rs.getString(3);
						if (s != null && s.length() != 0)
							mTab.setValue("InvoiceRule", s);
						//	DeliveryRule
						s = rs.getString(4);
						if (s != null && s.length() != 0)
							mTab.setValue("DeliveryRule", s);
						//	FreightCostRule
						s = rs.getString(5);
						if (s != null && s.length() != 0)
							mTab.setValue("FreightCostRule", s);
						//	DeliveryViaRule
						s = rs.getString(6);
						if (s != null && s.length() != 0)
							mTab.setValue("DeliveryViaRule", s);
					}
				}
				//  re-read customer rules
			}
			catch (SQLException e)
			{
	//			log.log(Level.SEVERE, sql, e);
				return e.getLocalizedMessage();
			}
			finally
			{
				DB.close(rs, pstmt);
				rs = null; pstmt = null;
			}
		}

		if(mField.getColumnName().equals("C_Opportunity_ID") && value!=null)
		{
			Integer C_Opportunity_ID = (Integer)value;

//			SELECT COALESCE(MAX(Version),0)+10 AS DefaultValue FROM JP_EstimationLine WHERE C_Opportunity_ID=@C_Opportunity_ID@

			String sql = "SELECT COALESCE(MAX(Version),0)+1 "			//1
					+ "FROM JP_Estimation "
					+ "WHERE C_Opportunity_ID=?";	//	#1
				PreparedStatement pstmt = null;
				ResultSet rs = null;

			try
			{
				pstmt = DB.prepareStatement(sql, null);
				pstmt.setInt(1, C_Opportunity_ID.intValue());
				//
				rs = pstmt.executeQuery();
				if (rs.next())
				{
					mTab.setValue("Version", rs.getBigDecimal(1));
				}
			}catch (SQLException e){
				//log.log(Level.SEVERE, sql, e);
				return e.getLocalizedMessage();
			}finally{
				DB.close(rs, pstmt);
				rs = null; pstmt = null;
				}
		}
		
		if(mField.getColumnName().equals("JP_LocatorTo_ID"))
		{
			if(value != null)
				mTab.setValue("JP_Locator_ID", value);
		}

		//JPIERE-0227
		if(mField.getColumnName().equals("JP_LocatorFrom_ID") && mTab.getValue("JP_Locator_ID") != null && mTab.getValue("JP_LocatorFrom_ID") != null)
		{
			Integer JP_Locator_ID = 	(Integer)mTab.getValue("JP_Locator_ID");
			MLocator shipLocator = MLocator.get(ctx, JP_Locator_ID.intValue());
		
			Integer JP_LocatorFrom_ID = 	(Integer)mTab.getValue("JP_LocatorFrom_ID");
			MLocator fromLocator =  MLocator.get(ctx, JP_LocatorFrom_ID.intValue());
			if(shipLocator.get_ValueAsInt("JP_PhysicalWarehouse_ID") == fromLocator.get_ValueAsInt("JP_PhysicalWarehouse_ID"))
			{
				mTab.setValue("JP_LocatorTo_ID",JP_Locator_ID) ;
			}
		}

		return "";
	}

}
