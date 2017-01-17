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
package jpiere.base.plugin.factory;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;
import org.compiere.model.MInOut;
import org.compiere.model.MLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MPayment;

import jpiere.base.plugin.org.adempiere.callout.JPiereBankAcountCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereBillAmountCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereBillBPartnerCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereCityCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereDropShipBPartnerCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereEstimationCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereInOutCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereInvValAdjustCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereInvValCalCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereInvValProfileCallout;
import jpiere.base.plugin.org.adempiere.callout.JPiereRegionCallout;
import jpiere.base.plugin.org.adempiere.model.MBill;
import jpiere.base.plugin.org.adempiere.model.MEstimation;
import jpiere.base.plugin.org.adempiere.model.MInvValAdjust;
import jpiere.base.plugin.org.adempiere.model.MInvValCal;
import jpiere.base.plugin.org.adempiere.model.MInvValProfile;

/**
 *  JPiere Base Plugin Callout Factory
 *
 *  JPIERE-0106:JPBP:Bill
 *
 *  @author Hideaki Hagiwara(h.hagiwara@oss-erp.co.jp)
 *
 */
public class JPiereBasePluginColumnCalloutFactory implements IColumnCalloutFactory {

	@Override
	public IColumnCallout[] getColumnCallouts(String tableName, String columnName) {

		List<IColumnCallout> list = new ArrayList<IColumnCallout>();

		if(tableName.startsWith("JP"))
		{
			if(tableName.equals(MBill.Table_Name))
			{

				if(columnName.equals(MBill.COLUMNNAME_C_BPartner_ID))
				{
					list.add(new JPiereBillBPartnerCallout());

				}else if(columnName.equals(MBill.COLUMNNAME_JP_LastBill_ID)
							|| columnName.equals(MBill.COLUMNNAME_JPLastBillAmt)
							|| columnName.equals(MBill.COLUMNNAME_C_Payment_ID)
							|| columnName.equals(MBill.COLUMNNAME_JPLastPayAmt))
				{
					list.add(new JPiereBillAmountCallout());
				}
			}else if(tableName.equals(MInvValProfile.Table_Name)){
				if(columnName.equals(MInvValProfile.COLUMNNAME_C_AcctSchema_ID))
				{
					list.add(new JPiereInvValProfileCallout());
				}
			}else if(tableName.equals(MInvValCal.Table_Name)){	//JPIERE-0161
				if(columnName.equals(MInvValCal.COLUMNNAME_JP_InvValProfile_ID)
						|| columnName.equals(MInvValCal.COLUMNNAME_DateValue))
				{
					list.add(new JPiereInvValCalCallout());
				}
			}else if(tableName.equals(MInvValAdjust.Table_Name)){	//JPIERE-0163
				if(columnName.equals(MInvValAdjust.COLUMNNAME_JP_InvValProfile_ID)
						|| columnName.equals(MInvValCal.COLUMNNAME_DateValue))
				{
					list.add(new JPiereInvValAdjustCallout());
				}
			}else if(tableName.equals(MEstimation.Table_Name)){	//JPIERE-0183
				if(columnName.equals(MEstimation.COLUMNNAME_JP_DocTypeSO_ID)
						|| columnName.equals(MEstimation.COLUMNNAME_C_DocTypeTarget_ID)
						|| columnName.equals(MEstimation.COLUMNNAME_C_Opportunity_ID))
				{
					list.add(new JPiereEstimationCallout());
				}
			}


		}else{

			if(tableName.equals(MPayment.Table_Name))
			{
				if(columnName.equals(MPayment.COLUMNNAME_C_BankAccount_ID))
					list.add(new JPiereBankAcountCallout());

			}else if(tableName.equals(MLocation.Table_Name)){

				if(columnName.equals(MLocation.COLUMNNAME_C_Region_ID))
					list.add(new JPiereRegionCallout());
				else if(columnName.equals(MLocation.COLUMNNAME_C_City_ID))
					list.add(new JPiereCityCallout());

			}else if(tableName.equals(MOrder.Table_Name)){

				if(columnName.equals(MOrder.COLUMNNAME_DropShip_BPartner_ID))
					list.add(new JPiereDropShipBPartnerCallout());

			}else if(tableName.equals(MInOut.Table_Name)){

				if(columnName.equals(MInOut.COLUMNNAME_C_Order_ID))
					list.add(new JPiereInOutCallout());
				else if(columnName.equals(MOrder.COLUMNNAME_DropShip_BPartner_ID))
					list.add(new JPiereDropShipBPartnerCallout());
			}
		}

		return list != null ? list.toArray(new IColumnCallout[0]) : new IColumnCallout[0];
	}

}
