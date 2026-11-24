package io.ddd4j.sample.dropwizard.cqrs.persistent;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
/** 真实 PostgreSQL 调用前的一次性故障注入，所有成功 SQL 仍执行真实数据库。 */
final class FailingDataSource implements DataSource {
 final AtomicBoolean failAck=new AtomicBoolean();final AtomicBoolean failQuery=new AtomicBoolean();private final DataSource delegate;
 FailingDataSource(DataSource delegate){this.delegate=delegate;}
 private Connection wrap(Connection connection){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
  if("prepareStatement".equals(method.getName()) && args[0] instanceof String){String sql=(String)args[0];
   if((sql.startsWith("UPDATE sample_order_outbox") && failAck.getAndSet(false)) || (sql.startsWith("INSERT INTO sample_order_read_models") && failQuery.getAndSet(false))){throw new SQLException("injected commit-boundary failure");}
  }
  try{return method.invoke(connection,args);}catch(InvocationTargetException error){throw error.getCause();}
 });}
 @Override public Connection getConnection() throws SQLException{return wrap(delegate.getConnection());}
 @Override public Connection getConnection(String user,String password)throws SQLException{return wrap(delegate.getConnection(user,password));}
 @Override public java.io.PrintWriter getLogWriter()throws SQLException{return delegate.getLogWriter();}
 @Override public void setLogWriter(java.io.PrintWriter writer)throws SQLException{delegate.setLogWriter(writer);}
 @Override public void setLoginTimeout(int timeout)throws SQLException{delegate.setLoginTimeout(timeout);}
 @Override public int getLoginTimeout()throws SQLException{return delegate.getLoginTimeout();}
 @Override public java.util.logging.Logger getParentLogger()throws java.sql.SQLFeatureNotSupportedException{return delegate.getParentLogger();}
 @Override public <T>T unwrap(Class<T> type)throws SQLException{return delegate.unwrap(type);}
 @Override public boolean isWrapperFor(Class<?> type)throws SQLException{return delegate.isWrapperFor(type);}
}
