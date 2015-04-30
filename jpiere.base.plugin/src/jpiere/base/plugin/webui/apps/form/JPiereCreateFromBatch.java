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
package jpiere.base.plugin.webui.apps.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Vector;
import java.util.logging.Level;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 *
 * @author Elaine
 * @author Hideaki Hagiwara
 *
 */
public abstract class JPiereCreateFromBatch extends CreateFrom
{
	public JPiereCreateFromBatch(GridTab gridTab)
	{
		super(gridTab);
	}

	public String getSQLWhere(Object BPartner, String DocumentNo, Object DateFrom, Object DateTo,
			Object AmtFrom, Object AmtTo, Object DocType, Object TenderType, String AuthCode)
	{
		StringBuilder sql = new StringBuilder();
		sql.append("WHERE p.Processed='Y' AND p.IsReconciled='N'");
		sql.append(" AND p.DocStatus IN ('CO','CL') AND p.PayAmt<>0");//JPIERE-0091 'VO'と 'RE'を削除した
		sql.append(" AND p.C_BankAccount_ID = ?");
	    sql.append(" AND NOT EXISTS (SELECT * FROM C_BankStatementLine l WHERE p.C_Payment_ID=l.C_Payment_ID AND l.StmtAmt <> 0)");

	    if(DocType != null)
			sql.append(" AND p.C_DocType_ID=?");
	    if(TenderType != null && TenderType.toString().length() > 0)
			sql.append(" AND p.TenderType=?");
		if(BPartner != null)
			sql.append(" AND p.C_BPartner_ID=?");

		if(DocumentNo.length() > 0)
			sql.append(" AND UPPER(p.DocumentNo) LIKE ?");
		if(AuthCode.length() > 0)
			sql.append(" AND p.R_AuthCode LIKE ?");

		if(AmtFrom != null || AmtTo != null)
		{
			BigDecimal from = (BigDecimal) AmtFrom;
			BigDecimal to = (BigDecimal) AmtTo;
			if(from == null && to != null)
				sql.append(" AND p.PayAmt <= ?");
			else if(from != null && to == null)
				sql.append(" AND p.PayAmt >= ?");
			else if(from != null && to != null)
				sql.append(" AND p.PayAmt BETWEEN ? AND ?");
		}

		if(DateFrom != null || DateTo != null)
		{
			Timestamp from = (Timestamp) DateFrom;
			Timestamp to = (Timestamp) DateTo;
			if(from == null && to != null)
				sql.append(" AND TRUNC(p.DateTrx) <= ?");
			else if(from != null && to == null)
				sql.append(" AND TRUNC(p.DateTrx) >= ?");
			else if(from != null && to != null)
				sql.append(" AND TRUNC(p.DateTrx) BETWEEN ? AND ?");
		}

		if (log.isLoggable(Level.FINE)) log.fine(sql.toString());
		return sql.toString();
	}

	void setParameters(PreparedStatement pstmt, Object BankAccount, Object BPartner, String DocumentNo, Object DateFrom, Object DateTo,
			Object AmtFrom, Object AmtTo, Object DocType, Object TenderType, String AuthCode)
	throws SQLException
	{
		//  Get StatementDate
		//Timestamp ts = (Timestamp) getGridTab().getValue("StatementDate");
		//if (ts == null)
			//ts = new Timestamp(System.currentTimeMillis());

		int index = 1;

		//pstmt.setTimestamp(index++, ts);
		pstmt.setInt(index++, BankAccount != null ? (Integer) BankAccount : (Integer) getGridTab().getValue("C_BankAccount_ID"));

		if(DocType != null)
			pstmt.setInt(index++, (Integer) DocType);

		if(TenderType != null && TenderType.toString().length() > 0)
			pstmt.setString(index++, (String) TenderType);

		if(BPartner != null)
			pstmt.setInt(index++, (Integer) BPartner);

		if(DocumentNo.length() > 0)
			pstmt.setString(index++, getSQLText(DocumentNo));

		if(AuthCode.length() > 0)
			pstmt.setString(index++, getSQLText(AuthCode));

		if(AmtFrom != null || AmtTo != null)
		{
			BigDecimal from = (BigDecimal) AmtFrom;
			BigDecimal to = (BigDecimal) AmtTo;
			if (log.isLoggable(Level.FINE)) log.fine("Amt From=" + from + ", To=" + to);
			if(from == null && to != null)
				pstmt.setBigDecimal(index++, to);
			else if(from != null && to == null)
				pstmt.setBigDecimal(index++, from);
			else if(from != null && to != null)
			{
				pstmt.setBigDecimal(index++, from);
				pstmt.setBigDecimal(index++, to);
			}
		}

		if(DateFrom != null || DateTo != null)
		{
			Timestamp from = (Timestamp) DateFrom;
			Timestamp to = (Timestamp) DateTo;
			if (log.isLoggable(Level.FINE)) log.fine("Date From=" + from + ", To=" + to);
			if(from == null && to != null)
				pstmt.setTimestamp(index++, to);
			else if(from != null && to == null)
				pstmt.setTimestamp(index++, from);
			else if(from != null && to != null)
			{
				pstmt.setTimestamp(index++, from);
				pstmt.setTimestamp(index++, to);
			}
		}
	}

	private String getSQLText(String text)
	{
		String s = text.toUpperCase();
		if(!s.endsWith("%"))
			s += "%";
		if (log.isLoggable(Level.FINE)) log.fine( "String=" + s);
		return s;
	}

	protected abstract Vector<Vector<Object>> getBankAccountData(Object BankAccount, Object BPartner, String DocumentNo,
			Object DateFrom, Object DateTo, Object AmtFrom, Object AmtTo, Object DocType, Object TenderType, String AuthCode);

	public void info(IMiniTable miniTable, IStatusBar statusBar)
	{
		DecimalFormat format = DisplayType.getNumberFormat(DisplayType.Amount);
		BigDecimal total = Env.ZERO;
		int rows = miniTable.getRowCount();
		int count = 0;
		for(int i = 0; i < rows; i++)
		{
			if(((Boolean) miniTable.getValueAt(i, 0)).booleanValue())
			{
				total = total.add((BigDecimal) miniTable.getValueAt(i, 4));
				count++;
			}
		}
		statusBar.setStatusLine(String.valueOf(count) + " - " + Msg.getMsg(Env.getCtx(), "Sum") + "  " + format.format(total));
	}
}