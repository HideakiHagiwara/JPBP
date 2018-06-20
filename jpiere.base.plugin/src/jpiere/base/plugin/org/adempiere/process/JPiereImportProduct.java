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
package jpiere.base.plugin.org.adempiere.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

import org.adempiere.model.ImportValidator;
import org.adempiere.process.ImportProcess;
import org.adempiere.util.IProcessUI;
import org.compiere.model.MColumn;
import org.compiere.model.MProduct;
import org.compiere.model.MProductPO;
import org.compiere.model.MTable;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.PO;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

import jpiere.base.plugin.org.adempiere.model.X_I_ProductJP;

/**
 *	JPIERE-0096:Import Products from I_ProductJP
 *
 * 	@author 	Jorg Janke
 * 	@version 	$Id: ImportProduct.java,v 1.3 2006/07/30 00:51:01 jjanke Exp $
 *
 *  @author Carlos Ruiz, globalqss
 * 			<li>FR [ 2788278 ] Data Import Validator - migrate core processes
 * 				https://sourceforge.net/tracker/?func=detail&aid=2788278&group_id=176962&atid=879335
 *
 *  @author Hideaki Hagiwara
 */
public class JPiereImportProduct extends SvrProcess implements ImportProcess
{
	/**	Client to be imported to		*/
	private int				m_AD_Client_ID = 0;
	/**	Delete old Imported				*/
	private boolean			m_deleteOldImported = false;


	private IProcessUI processMonitor = null;

	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (name.equals("AD_Client_ID"))
				m_AD_Client_ID = ((BigDecimal)para[i].getParameter()).intValue();
			else if (name.equals("DeleteOldImported"))
				m_deleteOldImported = "Y".equals(para[i].getParameter());
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}	//	prepare

	/**
	 *  Perform process.
	 *  @return Message
	 *  @throws Exception
	 */
	protected String doIt() throws java.lang.Exception
	{
		processMonitor = Env.getProcessUI(getCtx());

		StringBuilder sql = null;
		int no = 0;
		String clientCheck = getWhereClause();

		if (m_deleteOldImported)
		{
			sql = new StringBuilder ("DELETE I_ProductJP ")
				.append("WHERE I_IsImported='Y'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.INFO)) log.info("Delete Old Imported =" + no);
		}


		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_BEFORE_VALIDATE);

		//Reverse Lookup Surrogate Key
		reverseLookupM_Product_ID();
		reverseLookupAD_Org_ID();
		reverseLookupM_Product_Category_ID();
		reverseLookupC_TaxCategory_ID();
		reverseLookupC_UOM_ID();
		reverseLookupM_FreightCategory_ID();
		reverseLookupM_PartType_ID();
		reverseLookupS_ExpenseType_ID();
		reverseLookupS_Resource_ID();
		reverseLookupM_AttributeSet_ID();
		reverseLookupSalesRep_ID();
		reverseLookupR_MailText_ID();
		reverseLookupM_Locator_ID();

		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_AFTER_VALIDATE);

		commitEx();

		sql = new StringBuilder ("SELECT * FROM I_ProductJP WHERE I_IsImported='N' ")
				.append(clientCheck).append(" ORDER BY Value ");
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
			rs = pstmt.executeQuery();
			String preValue = "";
			while (rs.next())
			{
				X_I_ProductJP imp = new X_I_ProductJP (getCtx (), rs, get_TrxName());

				boolean isNew = true;
				if(imp.getM_Product_ID()!=0)
				{
					isNew =false;

				}else{

					if(preValue.equals(imp.getValue()))
					{
						isNew = false;

					}else {

						preValue = imp.getValue();

					}

				}

				if(isNew)
				{
					createNewProduct(imp);

				}else{

					updateProduct(imp);
				}

			}//while

		}catch (Exception e) {

			log.log(Level.SEVERE, e.toString(), e);

		}finally{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}


		return "";
	}	//	doIt


	@Override
	public String getImportTableName() {
		return X_I_ProductJP.Table_Name;
	}


	@Override
	public String getWhereClause() {
		StringBuilder msgreturn = new StringBuilder(" AND AD_Client_ID=").append(m_AD_Client_ID);
		return msgreturn.toString();
	}

	/**
	 * Reverse Look up Product From Value and UPC , VendorProduct No
	 * @throws Exception
	 *
	 */
	private void reverseLookupM_Product_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse lookup M_Product_ID From Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product p")
				.append(" WHERE i.Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.M_Product_ID IS NULL AND i.Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Reverse lookup M_Product_ID From UPC
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "UPC") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product p")
			.append(" WHERE i.UPC=p.UPC AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append(" WHERE i.UPC IS NOT NULL AND i.M_Product_ID IS NULL")
			.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Reverse lookup C_BPartner_ID From BPartner_Value for Update M_Product_ID From VendorProductNo
		reverseLookupC_BPartner_ID();

		//Reverse lookup M_Product_ID From VendorProductNo
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_ID")
				+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "VendorProductNo") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product_po p")
			.append(" WHERE i.C_BPartner_ID=p.C_BPartner_ID")
			.append(" AND i.VendorProductNo=p.VendorProductNo AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE i.VendorProductNo IS NOT NULL AND i.M_Product_ID IS NULL")
			.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + sql );
		}

		//Error : Search Key is null
		msg = Msg.getMsg(getCtx(), "JP_Null")+Msg.getElement(getCtx(), "Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE Value IS NULL AND M_Product_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}


	}//reverseLookupM_Product_ID

	/**
	 * Reverse look up C_BPartner_ID From BPartner_Value for Update M_Product_ID From VendorProductNo
	 *
	 * @throws Exception
	 */
	private void reverseLookupC_BPartner_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		//Reverse lookup C_BPartner_ID From BPartner_Value for Update M_Product_ID From VendorProductNo
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "C_BPartner_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "BPartner_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET C_BPartner_ID=(SELECT C_BPartner_ID FROM C_BPartner p")
			.append(" WHERE i.BPartner_Value=p.Value AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE i.C_BPartner_ID IS NULL AND i.BPartner_Value IS NOT NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine("Found Business Partner =" + no);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid BPartner_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "BPartner_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append("WHERE C_BPartner_ID IS NULL AND BPartner_Value IS NOT NULL")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupC_BPartner_ID


	/**
	 * Reverse Look up Organization From JP_Org_Value
	 *
	 **/
	private void reverseLookupAD_Org_ID() throws Exception
	{

		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		 msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "AD_Org_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Look up AD_Org ID From JP_Org_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "AD_Org_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_Org_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET AD_Org_ID=(SELECT AD_Org_ID FROM AD_org p")
				.append(" WHERE i.JP_Org_Value=p.Value AND (p.AD_Client_ID=i.AD_Client_ID or p.AD_Client_ID=0) AND p.IsSummary='N') ")
				.append(" WHERE i.JP_Org_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + sql );
		}

		//Invalid JP_Org_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_Org_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE AD_Org_ID = 0 AND JP_Org_Value IS NOT NULL AND JP_Org_Value <> '0' ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupAD_Org_ID

	/**
	 * Reverse Look up Product Category From ProductCategory_Value
	 *
	 * @throws Exception
	 */
	private void reverseLookupM_Product_Category_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_Category_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Look up M_Product_Category_ID From ProuctCategory_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_Category_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "ProductCategory_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_Product_Category_ID=(SELECT M_Product_Category_ID FROM M_Product_Category p")
				.append(" WHERE i.ProductCategory_Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.ProductCategory_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid ProuctCategory_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "ProductCategory_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE ProductCategory_Value IS NOT NULL AND M_Product_Category_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

		//Set Default Product Category in case of New Product
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Product_Category_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "IsDefault") ;
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET ProductCategory_Value=(SELECT MAX(Value) FROM M_Product_Category")
			.append(" WHERE IsDefault='Y' AND AD_Client_ID=").append(m_AD_Client_ID).append(") ")
			.append("WHERE ProductCategory_Value IS NULL AND M_Product_Category_ID IS NULL")
			.append(" AND M_Product_ID IS NULL")	//	set category only if product not found
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

	} //reverseLookupM_Product_Category_ID

	/**
	 *
	 * Reverse Look up C_TaxCategory_ID From JP_TaxCategory_Name
	 *
	 * @throws Exception
	 */
	private void reverseLookupC_TaxCategory_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "C_TaxCategory_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);


		//Look up C_TaxCategory_ID From JP_TaxCategory_Name
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "C_TaxCategory_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_TaxCategory_Name") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET C_TaxCategory_ID=(SELECT C_TaxCategory_ID FROM C_TaxCategory p")
				.append(" WHERE i.JP_TaxCategory_Name=p.Name AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_TaxCategory_Name IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_TaxCategory_Name
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_TaxCategory_Name");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_TaxCategory_Name IS NOT NULL AND C_TaxCategory_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupC_TaxCategory_ID


	/**
	 * Reverse Look up C_UOM_ID From X12DE355
	 *
	 * @throws Exception
	 */
	private void reverseLookupC_UOM_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		//Look up C_UOM_ID From X12DE355
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "C_UOM_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "X12DE355") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET C_UOM_ID=(SELECT C_UOM_ID FROM C_UOM p")
				.append(" WHERE i.X12DE355=p.X12DE355 AND (i.AD_Client_ID=p.AD_Client_ID OR p.AD_Client_ID = 0) ) ")
				.append("WHERE X12DE355 IS NOT NULL")
				.append(" AND I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid X12DE355
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "X12DE355");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE X12DE355 IS NOT NULL AND C_UOM_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupC_UOM_ID


	/**
	 * Reverse Look up Freight Category From JP_FreightCategory_Value
	 *
	 * @throws Exception
	 */
	private void reverseLookupM_FreightCategory_ID()throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_FreightCategory_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Look up M_FreightCategory_ID From JP_FreightCategory_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_FreightCategory_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_FreightCategory_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_FreightCategory_ID=(SELECT M_FreightCategory_ID FROM M_FreightCategory p")
				.append(" WHERE i.JP_FreightCategory_Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_FreightCategory_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_FreightCategory_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_FreightCategory_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_FreightCategory_Value IS NOT NULL AND M_FreightCategory_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupM_FreightCategory_ID


	/**
	 * Reverse Look up M_PartType_ID From JP_PartType_Nam
	 *
	 * @throws Exception
	 */
	private void reverseLookupM_PartType_ID()throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_PartType_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Look up M_PartType_ID From JP_PartType_Name
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_PartType_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_PartType_Name") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_PartType_ID=(SELECT M_PartType_ID FROM M_PartType p")
				.append(" WHERE i.JP_PartType_Name=p.Name AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_PartType_Name IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_PartType_Name
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_PartType_Name");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_PartType_Name IS NOT NULL AND M_PartType_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupM_PartType_ID


	/**
	 * Reverse look up M_PartType_ID From JP_PartType_Name
	 *
	 * @throws Exception
	 */
	private void reverseLookupS_ExpenseType_ID()throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "S_ExpenseType_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse look up M_PartType_ID From JP_PartType_Name
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "S_ExpenseType_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_ExpenseType_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET S_ExpenseType_ID=(SELECT S_ExpenseType_ID FROM S_ExpenseType p")
				.append(" WHERE i.JP_ExpenseType_Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_ExpenseType_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_ExpenseType_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_ExpenseType_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_ExpenseType_Value IS NOT NULL AND S_ExpenseType_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupS_ExpenseType_ID

	/**
	 * Reverse Look up S_Resource_ID From JP_Resource_Value
	 *
	 * @throws Exception
	 */
	private void reverseLookupS_Resource_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "S_Resource_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Lookup S_Resource_ID From JP_Resource_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "S_Resource_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_Resource_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET S_Resource_ID=(SELECT S_Resource_ID FROM S_Resource p")
				.append(" WHERE i.JP_Resource_Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_Resource_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_Resource_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_Resource_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_Resource_Value IS NOT NULL AND S_Resource_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupS_Resource_ID

	/**
	 *
	 * Reverse Look up M_AttributeSet_ID From JP_AttributeSet_Name
	 *
	 * @throws Exception
	 */
	private void reverseLookupM_AttributeSet_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_AttributeSet_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse Look up M_AttributeSet_ID From JP_AttributeSet_Name
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_AttributeSet_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_AttributeSet_Name") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_AttributeSet_ID=(SELECT M_AttributeSet_ID FROM M_AttributeSet p")
				.append(" WHERE i.JP_AttributeSet_Name=p.Name AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_AttributeSet_Name IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_AttributeSet_Name
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_AttributeSet_Name");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_AttributeSet_Name IS NOT NULL AND M_AttributeSet_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupM_AttributeSet_ID

	/**
	 * Reverse Look up SalesRep_ID From JP_User_Value
	 *
	 * @throws Exception
	 */
	private void reverseLookupSalesRep_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "SalesRep_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse Look up SalesRep_ID From JP_User_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "SalesRep_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_User_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET SalesRep_ID=(SELECT AD_User_ID FROM AD_User p")
				.append(" WHERE i.JP_User_Value=p.Value AND ( p.AD_Client_ID=i.AD_Client_ID OR p.AD_Client_ID=0 ) ) ")
				.append(" WHERE i.JP_User_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_User_Value
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_User_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_User_Value IS NOT NULL AND SalesRep_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseSalesRep_ID


	/**
	 * Reverse Look up R_MailText_ID From JP_MailText_Name
	 *
	 * @throws Exception
	 */
	private void reverseLookupR_MailText_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "R_MailText_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse Look up R_MailText_ID From JP_MailText_Name
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "R_MailText_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_MailText_Name") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET R_MailText_ID=(SELECT R_MailText_ID FROM R_MailText p")
				.append(" WHERE i.JP_MailText_Name=p.Name AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_MailText_Name IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_MailText_Name
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_MailText_Name");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_MailText_Name IS NOT NULL AND R_MailText_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupR_MailText_ID


	/**
	 * Reverse Look up M_Locator_ID From JP_Locator_Value
	 *
	 * @throws Exception
	 */
	private void reverseLookupM_Locator_ID() throws Exception
	{
		StringBuilder sql = new StringBuilder();
		String msg = new String();
		int no = 0;

		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Locator_ID");
		if (processMonitor != null)	processMonitor.statusUpdate(msg);

		//Reverse Look up M_Locator_ID From JP_Locator_Value
		msg = Msg.getMsg(getCtx(), "Matching") + " : " + Msg.getElement(getCtx(), "M_Locator_ID")
		+ " - " + Msg.getMsg(getCtx(), "MatchFrom") + " : " + Msg.getElement(getCtx(), "JP_Locator_Value") ;
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET M_Locator_ID=(SELECT M_Locator_ID FROM M_Locator p")
				.append(" WHERE i.JP_Locator_Value=p.Value AND p.AD_Client_ID=i.AD_Client_ID) ")
				.append(" WHERE i.JP_Locator_Value IS NOT NULL")
				.append(" AND i.I_IsImported='N'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		//Invalid JP_MailText_Name
		msg = Msg.getMsg(getCtx(), "Invalid")+Msg.getElement(getCtx(), "JP_Locator_Value");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_ErrorMsg='"+ msg + "'")
			.append(" WHERE JP_Locator_Value IS NOT NULL AND M_Locator_ID IS NULL ")
			.append(" AND I_IsImported<>'Y'").append(getWhereClause());
		try {
			no = DB.executeUpdateEx(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.FINE)) log.fine(msg +"=" + no + ":" + sql);
		}catch(Exception e) {
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg +" : " + sql );
		}

		if(no > 0)
		{
			commitEx();
			throw new Exception(Msg.getMsg(getCtx(), "Error") + msg );
		}

	}//reverseLookupM_Locator_ID



	/**
	 * Create New Product
	 *
	 * @param importProduct
	 * @throws SQLException
	 */
	private void createNewProduct(X_I_ProductJP importProduct) throws SQLException
	{
		MProduct newProduct = new MProduct(getCtx(), 0, get_TrxName());

		ModelValidationEngine.get().fireImportValidate(this, importProduct, newProduct, ImportValidator.TIMING_BEFORE_IMPORT);

		PO.copyValues(importProduct, newProduct);
		newProduct.setIsActive(importProduct.isI_IsActiveJP());
		ModelValidationEngine.get().fireImportValidate(this, importProduct, newProduct, ImportValidator.TIMING_AFTER_IMPORT);

		newProduct.saveEx(get_TrxName());

		if(importProduct.getC_BPartner_ID()> 0 )
		{
			createProductPOInfo(importProduct, newProduct.getM_Product_ID());
		}

		importProduct.setM_Product_ID(newProduct.getM_Product_ID());
		importProduct.setI_ErrorMsg(Msg.getMsg(getCtx(), "NewRecord"));
		importProduct.setI_IsImported(true);
		importProduct.setProcessed(true);
		importProduct.saveEx(get_TrxName());
		commitEx();

	}

	/**
	 *
	 * Update Product
	 *
	 * @param importProduct
	 * @throws SQLException
	 */
	private void updateProduct(X_I_ProductJP importProduct) throws SQLException
	{
		MProduct updateProduct = new MProduct(getCtx(), importProduct.getM_Product_ID(), get_TrxName());

		ModelValidationEngine.get().fireImportValidate(this, importProduct, updateProduct, ImportValidator.TIMING_BEFORE_IMPORT);

		//Update Product
		MTable M_Product_Table = MTable.get(getCtx(), MProduct.Table_ID, get_TrxName());
		MColumn[] M_Product_Columns = M_Product_Table.getColumns(true);

		MTable I_ProductJP_Table = MTable.get(getCtx(), X_I_ProductJP.Table_ID, get_TrxName());
		MColumn[] I_ProductJP_Columns = I_ProductJP_Table.getColumns(true);

		MColumn i_Column = null;
		for(int i = 0 ; i < M_Product_Columns.length; i++)
		{
			i_Column = M_Product_Columns[i];
			if(i_Column.isVirtualColumn() || i_Column.isKey() || i_Column.isUUIDColumn())
				continue;//i

			if(i_Column.getColumnName().equals("IsActive")
				|| i_Column.getColumnName().equals("IsStocked")
				|| i_Column.getColumnName().equals("ProductType")
				|| i_Column.getColumnName().equals("AD_Client_ID")
				|| i_Column.getColumnName().equals("Value")
				|| i_Column.getColumnName().equals("Processing")
				|| i_Column.getColumnName().equals("Created")
				|| i_Column.getColumnName().equals("CreatedBy")
				|| i_Column.getColumnName().equals("Updated")
				|| i_Column.getColumnName().equals("UpdatedBy") )
				continue;//i

			MColumn j_Column = null;
			Object importValue = null;
			for(int j = 0 ; j < I_ProductJP_Columns.length; j++)
			{
				j_Column = I_ProductJP_Columns[j];

				if(i_Column.getColumnName().equals(j_Column.getColumnName()))
				{
					importValue = importProduct.get_Value(j_Column.getColumnName());

					if(importValue == null )
					{
						break;//j

					}else if(importValue instanceof BigDecimal) {

						BigDecimal bigDecimal_Value = (BigDecimal)importValue;
						if(bigDecimal_Value.compareTo(Env.ZERO) == 0)
							break;

					}else if(j_Column.getAD_Reference_ID()==DisplayType.String) {

						String string_Value = (String)importValue;
						if(!Util.isEmpty(string_Value))
						{
							updateProduct.set_ValueNoCheck(i_Column.getColumnName(), importValue);
						}

						break;

					}else if(j_Column.getColumnName().endsWith("_ID")) {

						Integer p_key = (Integer)importValue;
						if(p_key.intValue() <= 0)
							break;
					}

					updateProduct.set_ValueNoCheck(i_Column.getColumnName(), importValue);
					break;
				}
			}//for j

		}//for i

		updateProduct.setIsActive(importProduct.isI_IsActiveJP());
		ModelValidationEngine.get().fireImportValidate(this, importProduct, updateProduct, ImportValidator.TIMING_AFTER_IMPORT);

		updateProduct.saveEx(get_TrxName());

		//Update Product Purchase Order Info
		if(importProduct.getC_BPartner_ID()> 0 )
		{
			MProductPO[] productPOs = MProductPO.getOfProduct(getCtx(), importProduct.getM_Product_ID(),  get_TrxName());
			boolean isNew = true;
			for(int i = 0; i < productPOs.length; i++)
			{
				if(productPOs[i].getC_BPartner_ID() == importProduct.getC_BPartner_ID())
				{
					updateProductPOInfo(importProduct, productPOs[i]);
					isNew = false;
					break;
				}
			}//for

			if(isNew)
			{
				createProductPOInfo(importProduct,importProduct.getM_Product_ID());
			}
		}

		importProduct.setI_ErrorMsg(Msg.getMsg(getCtx(), "Update"));
		importProduct.setI_IsImported(true);
		importProduct.setProcessed(true);
		importProduct.saveEx(get_TrxName());
		commitEx();

	}

	private void createProductPOInfo(X_I_ProductJP importProduct, int M_Product_ID)
	{
		MProductPO newProductPO = new MProductPO(getCtx(), 0, get_TrxName());
		ModelValidationEngine.get().fireImportValidate(this, importProduct, newProductPO, ImportValidator.TIMING_BEFORE_IMPORT);

		PO.copyValues(importProduct, newProductPO);
		newProductPO.setC_BPartner_ID(importProduct.getC_BPartner_ID());
		newProductPO.setM_Product_ID(importProduct.getM_Product_ID());
		newProductPO.setUPC(importProduct.getJP_VendorUPC());
		newProductPO.setC_UOM_ID(importProduct.getJP_VendorUOM_ID());
		newProductPO.setIsActive(importProduct.isI_IsActiveJP());
		ModelValidationEngine.get().fireImportValidate(this, importProduct, newProductPO, ImportValidator.TIMING_AFTER_IMPORT);

		newProductPO.saveEx(get_TrxName());
	}



	private void updateProductPOInfo(X_I_ProductJP importProduct, MProductPO updateProductPO)
	{
		ModelValidationEngine.get().fireImportValidate(this, importProduct, updateProductPO, ImportValidator.TIMING_BEFORE_IMPORT);

		//Update Product Info
		MTable M_ProductPO_Table = MTable.get(getCtx(), MProductPO.Table_ID, get_TrxName());
		MColumn[] M_ProductPO_Columns = M_ProductPO_Table.getColumns(true);

		MTable I_ProductJP_Table = MTable.get(getCtx(), X_I_ProductJP.Table_ID, get_TrxName());
		MColumn[] I_ProductJP_Columns = I_ProductJP_Table.getColumns(true);

		MColumn i_Column = null;
		for(int i = 0 ; i < M_ProductPO_Columns.length; i++)
		{
			i_Column = M_ProductPO_Columns[i];
			if(i_Column.isVirtualColumn() || i_Column.isKey() || i_Column.isUUIDColumn())
				continue;//i

			if(i_Column.getColumnName().equals("IsActive")
				|| i_Column.getColumnName().equals("Value")
				|| i_Column.getColumnName().equals("Processing")
				|| i_Column.getColumnName().equals("Created")
				|| i_Column.getColumnName().equals("CreatedBy")
				|| i_Column.getColumnName().equals("Updated")
				|| i_Column.getColumnName().equals("UpdatedBy") )
				continue;//i

			MColumn j_Column = null;
			Object importValue = null;
			for(int j = 0 ; j < I_ProductJP_Columns.length; j++)
			{
				j_Column = I_ProductJP_Columns[j];
				if(i_Column.getColumnName().equals(j_Column.getColumnName()))
				{
					importValue = importProduct.get_Value(j_Column.getColumnName());

					if(importValue == null )
					{
						break;//j

					}else if(importValue instanceof BigDecimal) {

						BigDecimal number = (BigDecimal)importProduct.get_Value(j_Column.getColumnName());
						if(number.compareTo(Env.ZERO) == 0)
							break;

					}else if(j_Column.getAD_Reference_ID()==DisplayType.String) {

						String string_Value = (String)importValue;
						if(!Util.isEmpty(string_Value))
						{
							updateProductPO.set_ValueNoCheck(i_Column.getColumnName(), importValue);
						}

						break;

					}else if(i_Column.getColumnName().endsWith("_ID")) {

						Integer p_key = (Integer)importProduct.get_Value(j_Column.getColumnName());
						if(p_key.intValue() <= 0)
							break;
					}

					updateProductPO.set_ValueNoCheck(i_Column.getColumnName(), importProduct.get_Value(j_Column.getColumnName()));
					break;
				}
			}//for j

		}//for i

		updateProductPO.setIsActive(importProduct.isI_IsActiveJP());
		ModelValidationEngine.get().fireImportValidate(this, importProduct, updateProductPO, ImportValidator.TIMING_AFTER_IMPORT);

		updateProductPO.saveEx(get_TrxName());
	}

}	//	ImportProduct
