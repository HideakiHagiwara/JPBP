/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2012 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package jpiere.base.plugin.webui.action.attachment;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.Service;
import org.adempiere.base.ServiceQuery;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.IArchiveStore;
import org.compiere.model.X_AD_StorageProvider;

public class MJPiereStorageProvider extends X_AD_StorageProvider {


	public MJPiereStorageProvider(Properties ctx, int AD_StorageProvider_ID, String trxName) {
		super(ctx, AD_StorageProvider_ID, trxName);
	}

	public MJPiereStorageProvider(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	public IJPiereAttachmentStore getAttachmentStore() {
		ServiceQuery query=new ServiceQuery();
		String method = this.getMethod();
		if (method == null)
			method = "DB";
		query.put("method", method);
		IJPiereAttachmentStore store = Service.locator().locate(IJPiereAttachmentStore.class, query).getService();
		if (store == null) {
			throw new AdempiereException("No attachment storage provider found");
		}
		return store;
	}

	public IArchiveStore getArchiveStore() {
//		ServiceQuery query=new ServiceQuery();
//		String method = this.getMethod();
//		if (method == null)
//			method = "DB";
//		query.put("method", method);
//		IArchiveStore store = Service.locator().locate(IArchiveStore.class, query).getService();
//		if (store == null) {
//			throw new AdempiereException("No archive storage provider found");
//		}
		return null;
	}

}