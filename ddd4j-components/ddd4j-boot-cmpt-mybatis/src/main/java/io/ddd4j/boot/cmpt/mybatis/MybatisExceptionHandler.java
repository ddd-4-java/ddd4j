/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.cmpt.mybatis;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.ApiRestResponse;
import io.ddd4j.boot.core.exception.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.plugin.PluginException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 异常增强，以JSON的形式返回给客服端
 * 异常增强类型：NullPointerException,RunTimeException,ClassCastException,
 * NoSuchMethodException,IOException,IndexOutOfBoundsException
 */
@ControllerAdvice
@ResponseBody
@Slf4j
public class MybatisExceptionHandler extends BaseExceptionHandler {

	@Autowired
	private NestedMessageSource messageSource;

	/**---------------------Mybatis 异常----------------------------*/

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ BindingException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisBindingException(BindingException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:绑定异常");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ CacheException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisCacheException(CacheException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:缓存异常");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ DataSourceException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisDataSourceException(DataSourceException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:数据源异常");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ PluginException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisPluginException(PluginException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:插件异常");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ ResultMapException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisResultMapException(ResultMapException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:结果集异常");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ TooManyResultsException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisTooManyResultsException(TooManyResultsException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis:结果集异常,返回了多条数据");
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ PersistenceException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisPersistenceException(PersistenceException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis 内部异常：" + ex.getMessage());
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 500 (Internal Server Error)
	 */
	@ExceptionHandler({ MybatisPlusException.class })
	public ResponseEntity<ApiRestResponse<String>> mybatisPlusException(MybatisPlusException ex) {
		this.logException(ex);
		ApiRestResponse<String> resp = ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse("MyBatis Plus 异常：" + ex.getMessage());
		return new ResponseEntity<>(resp, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	public NestedMessageSource getMessageSource() {
		return messageSource;
	}

}
