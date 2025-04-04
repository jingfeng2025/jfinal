/**
 * Copyright (c) 2011-2023, James Zhan 詹波 (jfinal@126.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jfinal.plugin.activerecord.tx;

import java.sql.Connection;
import java.sql.SQLException;
import com.jfinal.aop.Interceptor;
import com.jfinal.aop.Invocation;
import com.jfinal.kit.LogKit;
import com.jfinal.log.Log;
import com.jfinal.plugin.activerecord.ActiveRecordException;
import com.jfinal.plugin.activerecord.Config;
import com.jfinal.plugin.activerecord.DbKit;
import com.jfinal.plugin.activerecord.NestedTransactionHelpException;

/**
 * ActiveRecord declare transaction.
 * Example: @Before(Tx.class)
 */
public class Tx implements Interceptor {

	private static TxFun txFun = null;

	public static void setTxFun(TxFun txFun) {
		if (Tx.txFun != null) {
			Log.getLog(Tx.class).warn("txFun already set");
		}
		Tx.txFun = txFun;
	}

	public static TxFun getTxFun() {
		return Tx.txFun;
	}

	public static Config getConfigByTxConfig(Invocation inv) {
		TxConfig txConfig = inv.getMethod().getAnnotation(TxConfig.class);
		if (txConfig == null)
			txConfig = inv.getTarget().getClass().getAnnotation(TxConfig.class);

		if (txConfig != null) {
			Config config = DbKit.getConfig(txConfig.value());
			if (config == null)
				throw new RuntimeException("Config not found with TxConfig: " + txConfig.value());
			return config;
		}
		return null;
	}

	protected int getTransactionLevel(Config config) {
		return config.getTransactionLevel();
	}

	public void intercept(Invocation inv) {
		Config config = getConfigByTxConfig(inv);
		if (config == null)
			config = DbKit.getConfig();

		Connection conn = config.getThreadLocalConnection();
		if (conn != null) {	// Nested transaction support
			try {
				if (conn.getTransactionIsolation() < getTransactionLevel(config))
					conn.setTransactionIsolation(getTransactionLevel(config));

				if (txFun == null) {
				    inv.invoke();
				} else {
				    txFun.call(inv, conn);
				}

				return ;
			} catch (SQLException e) {
				throw new ActiveRecordException(e);
			}
		}

		Boolean autoCommit = null;
		try {
			conn = config.getConnection();
			autoCommit = conn.getAutoCommit();
			config.setThreadLocalConnection(conn);
			conn.setTransactionIsolation(getTransactionLevel(config));	// conn.setTransactionIsolation(transactionLevel);
			conn.setAutoCommit(false);

			if (txFun == null) {
				inv.invoke();
				conn.commit();
				config.executeCallbackAfterTxCommit();
			} else {
				txFun.call(inv, conn);
			}

		} catch (NestedTransactionHelpException e) {
			if (conn != null) try {conn.rollback();} catch (Exception e1) {LogKit.error(e1.getMessage(), e1);}
			LogKit.logNothing(e);
		} catch (Throwable t) {
			if (conn != null) try {conn.rollback();} catch (Exception e1) {LogKit.error(e1.getMessage(), e1);}

			// 支持在 controller 中 try catch 的 catch 块中使用 render(...) 并 throw e，实现灵活控制 render
			if (txFun == null && inv.isActionInvocation() && inv.getController().getRender() != null) {
				LogKit.error(t.getMessage(), t);
			} else {
				throw t instanceof RuntimeException ? (RuntimeException)t : new ActiveRecordException(t);
			}
		}
		finally {
			try {
				if (conn != null) {
					if (autoCommit != null)
						conn.setAutoCommit(autoCommit);
					conn.close();
				}
			} catch (Throwable t) {
				LogKit.error(t.getMessage(), t);	// can not throw exception here, otherwise the more important exception in previous catch block can not be thrown
			}
			finally {
				config.removeThreadLocalConnection();	// prevent memory leak
				config.removeCallbackAfterTxCommit();
			}
		}
	}
}



