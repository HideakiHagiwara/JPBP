/******************************************************************************
 * Product: JPiere(ジェイピエール) - JPiere Base Plugin                       *
 * Copyright (C) Hideaki Hagiwara All Rights Reserved.                        *
 * このプログラムはGNU Gneral Public Licens Version2のもと公開しています。    *
 * このプログラムは自由に活用してもらう事を期待して公開していますが、         *
 * いかなる保証もしていません。                                               *
 * 著作権は萩原秀明(h.hagiwara@oss-erp.co.jp)が保持し、サポートサービスは     *
 * 株式会社オープンソース・イーアールピー・ソリューションズで                 *
 * 提供しています。サポートをご希望の際には、                                 *
 * 株式会社オープンソース・イーアールピー・ソリューションズまでご連絡下さい。 *
 * http://www.oss-erp.co.jp/                                                  *
 *****************************************************************************/
package jpiere.base.plugin.org.adempiere.base;

import jpiere.base.plugin.util.ZenginCheck;

import org.compiere.model.MBank;
import org.compiere.model.MClient;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;


public class JPiereBankModelValidator implements ModelValidator {

	private static CLogger log = CLogger.getCLogger(JPiereBankModelValidator.class);
	private int AD_Client_ID = -1;
	private int AD_Org_ID = -1;
	private int AD_Role_ID = -1;
	private int AD_User_ID = -1;

	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		if(client != null)
			this.AD_Client_ID = client.getAD_Client_ID();
		engine.addModelChange(MBank.Table_Name, this);
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
	public String modelChange(PO po, int type) throws Exception {

		//JPIERE-0102
		if(type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE)
		{

			MBank bank = (MBank)po;
			String characters = (String)bank.get_Value("JP_BankName_Kana");
			if(characters != null)
			{
				for(int i = 0; i < characters.length(); i++)
				{
					if(!ZenginCheck.charCheck(characters.charAt(i)))
					{
						return "「"+characters.charAt(i) + "」は使えない文字です。";
					}
				}//for

				if(characters.length() > ZenginCheck.JP_BankName_Kana)
				{
					return Msg.getElement(Env.getCtx(), "JP_BankName_Kana") + "は" + ZenginCheck.JP_BankName_Kana + "以内です。";
				}

				if(bank.getRoutingNo().length()!= ZenginCheck.JP_RoutingNo)
				{
					return Msg.getElement(Env.getCtx(), "RoutingNo") + "は" + ZenginCheck.JP_RoutingNo + "桁です。";
				}

				if(!ZenginCheck.numStringCheck(bank.getRoutingNo()))
				{
					return Msg.getElement(Env.getCtx(), "RoutingNo") + "に半角数値以外の文字が使用されています。";
				}

			}//if(characters != null)

		}

		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {
		// TODO 自動生成されたメソッド・スタブ
		return null;
	}



}