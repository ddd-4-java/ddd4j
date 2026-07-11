package io.ddd4j.data.mybatis.adapter;

import com.baomidou.mybatisplus.enhance.observation.SqlObservation;
import com.baomidou.mybatisplus.enhance.observation.SqlObservationListener;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;

import java.util.Objects;

/**
 * Stores generic SQL observations in the ddd4j request ThreadContext.
 */
public class Ddd4jSqlObservationListener implements SqlObservationListener {

    @Override
    public void onCompleted(SqlObservation observation) {
        if (Objects.isNull(observation)) {
            return;
        }
        String sql = observation.getSql();
        if (Objects.nonNull(sql)) {
            ThreadContext.set(ContextConstants.PREPARING_SQL, sql.replaceAll("\\s+", " ").trim());
        }
        ThreadContext.set(ContextConstants.LAST_SQL_SPENDS, observation.getElapsedMillis());
    }

}
