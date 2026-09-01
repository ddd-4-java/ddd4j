package io.ddd4j.data.repository.impl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.Query;
import io.ddd4j.core.contract.constant.ContextConstants;
import io.ddd4j.data.annotation.BizKey;
import io.ddd4j.data.annotation.TenantId;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link BaseRepositoryImpl} tests.
 */
class BaseRepositoryImplTest {

    private DemoMapper mapper;
    private BaseRepositoryImpl<DemoMapper, DemoModel, DemoPO, DemoQuery> repository;

    @BeforeEach
    void setUp() {
        ThreadContext.clear();
        mapper = mock(DemoMapper.class);
        repository = new BaseRepositoryImpl<DemoMapper, DemoModel, DemoPO, DemoQuery>() {
        };
        ReflectionTestUtils.setField(repository, "mapper", mapper);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clear();
    }

    @Test
    void shouldInsertViaMapperForSave() {
        DemoModel model = new DemoModel();
        model.setName("n1");
        model.setBizKey("BK-1");
        when(mapper.insert(any(DemoPO.class))).thenReturn(1);

        boolean result = repository.save(model);

        assertTrue(result);
        ArgumentCaptor<DemoPO> captor = ArgumentCaptor.forClass(DemoPO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("n1", captor.getValue().getName());
        assertEquals("BK-1", captor.getValue().getBizKey());
    }

    @Test
    void shouldUpdateByIdViaMapperForUpdate() {
        DemoModel model = new DemoModel();
        model.setId(1L);
        model.setName("n2");
        DemoPO updated = new DemoPO();
        updated.setId(1L);
        updated.setName("n2");
        when(mapper.updateById(any(DemoPO.class))).thenReturn(1);
        when(mapper.selectById(any(Serializable.class))).thenReturn(updated);

        boolean result = repository.update(model);

        assertTrue(result);
        verify(mapper).updateById(any(DemoPO.class));
    }

    @Test
    void shouldCallInsertOrUpdateBasedOnSelectCountForSaveOrUpdate() {
        DemoModel model = new DemoModel();
        model.setId(1L);
        model.setBizKey("BK-1");

        // 分支一：记录不存在（selectCount == 0）→ 走 insert
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(DemoPO.class))).thenReturn(1);

        assertTrue(repository.saveOrUpdate(model));
        verify(mapper).insert(any(DemoPO.class));
        verify(mapper, never()).updateById(any(DemoPO.class));

        // 分支二：记录已存在（selectCount > 0）→ 走 updateById
        reset(mapper);
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.updateById(any(DemoPO.class))).thenReturn(1);
        when(mapper.selectById(any(Serializable.class))).thenReturn(new DemoPO());

        assertTrue(repository.saveOrUpdate(model));
        verify(mapper).updateById(any(DemoPO.class));
        verify(mapper, never()).insert(any(DemoPO.class));
    }

    @Test
    void shouldRejectBatchDeleteOver100Records() {
        List<Serializable> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add((long) i);
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> repository.delete(ids));
        assertTrue(ex.getMessage().contains("100"));
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldQueryByKeyForListByKey() {
        DemoPO po = new DemoPO();
        po.setId(1L);
        po.setBizKey("BK-1");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(po));

        List<DemoModel> result = repository.listByKey(Arrays.asList("BK-1", "BK-2"));

        verify(mapper).selectList(any());
        assertEquals(1, result.size());
        assertEquals("BK-1", result.get(0).getBizKey());
    }

    @Test
    void shouldBypassPaginationWhenSizeIsNegative() {
        DemoQuery query = new DemoQuery();
        query.setSize(-1);
        DemoPO po1 = new DemoPO();
        po1.setId(1L);
        DemoPO po2 = new DemoPO();
        po2.setId(2L);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(po1, po2));

        Page<DemoModel> page = repository.page(query);

        verify(mapper).selectList(any());
        verify(mapper, never()).selectPage(any(), any());
        assertEquals(2, page.getRecords().size());
        assertEquals(2L, page.getTotal());
        assertEquals(1L, page.getCurrent());
        assertEquals(2L, page.getSize());
    }

    @Test
    void shouldCallFillHookBeforeReturning() {
        AtomicBoolean fillCalled = new AtomicBoolean(false);
        AtomicReference<List<DemoModel>> filledModels = new AtomicReference<>();
        BaseRepositoryImpl<DemoMapper, DemoModel, DemoPO, DemoQuery> fillRepository =
                new BaseRepositoryImpl<DemoMapper, DemoModel, DemoPO, DemoQuery>() {
                    @Override
                    public void fill(DemoQuery query, List<DemoModel> models) {
                        fillCalled.set(true);
                        filledModels.set(models);
                    }
                };
        ReflectionTestUtils.setField(fillRepository, "mapper", mapper);
        DemoPO po = new DemoPO();
        po.setId(1L);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(po));

        List<DemoModel> result = fillRepository.list(new DemoQuery());

        assertTrue(fillCalled.get(), "fill hook should be invoked before returning");
        assertSame(result, filledModels.get());
        assertEquals(1, result.size());
    }

    @Test
    void shouldRespectTenantIdInInsertFill() {
        ThreadContext.set(ContextConstants.TENANT_ID, "7");
        DemoModel model = new DemoModel();
        model.setName("tenant");
        when(mapper.insert(any(DemoPO.class))).thenReturn(1);

        assertTrue(repository.save(model));

        ArgumentCaptor<DemoPO> captor = ArgumentCaptor.forClass(DemoPO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(Long.valueOf(7L), captor.getValue().getTenantId());
    }

    @Test
    void shouldMapFieldsUnderlineForPoToModel() {
        assertEquals("biz_key", BaseRepositoryImpl.TableScheme.toUnderline("bizKey"));
        assertEquals("tenant_id", BaseRepositoryImpl.TableScheme.toUnderline("tenantId"));
        assertEquals("id", BaseRepositoryImpl.TableScheme.toUnderline("id"));

        DemoPO po = new DemoPO();
        po.setId(3L);
        po.setBizKey("BK-9");
        po.setName("n9");
        po.setTenantId(7L);

        DemoModel model = BaseRepositoryImpl.convert(po);

        assertEquals(Long.valueOf(3L), model.getId());
        assertEquals("BK-9", model.getBizKey());
        assertEquals("n9", model.getName());
    }

    @Test
    void shouldReturnCorrectCountFromMapper() {
        when(mapper.selectCount(any())).thenReturn(5L);
        assertEquals(5, repository.count(new DemoQuery()));

        when(mapper.selectCount(any())).thenReturn(null);
        assertEquals(0, repository.count(new DemoQuery()));
    }

    // ========================= Fixtures =========================

    interface DemoMapper extends BaseMapper<DemoPO> {
    }

    @Data
    @TableName("demo")
    public static class DemoPO {
        @TableId(type = IdType.AUTO)
        private Long id;
        @BizKey
        private String bizKey;
        @TenantId
        private Long tenantId;
        private String name;
    }

    @Data
    public static class DemoModel extends Model {
        private Long id;
        private String bizKey;
        private String name;
    }

    public static class DemoQuery extends Query {
    }
}
