package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiException;
import com.green.dto.PlantAliasDTO;
import com.green.entity.PlantAlias;
import com.green.mapper.PlantAliasMapper;
import com.green.mapper.PlantsMapper;
import com.green.service.IPlantAliasService;
import com.green.vo.PlantAliasVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlantAliasServiceImpl extends ServiceImpl<PlantAliasMapper, PlantAlias> implements IPlantAliasService {

    private static final Set<String> ALIAS_TYPES = new HashSet<>(Arrays.asList(
            "COMMON_ALIAS", "TYPO_VARIANT", "FORMER_SCIENTIFIC_NAME", "REGIONAL_NAME"
    ));

    @Autowired
    private PlantsMapper plantsMapper;

    @Override
    public List<PlantAliasVO> listByPlantId(String plantId) {
        ensurePlantExists(plantId);
        return list(new LambdaQueryWrapper<PlantAlias>()
                .eq(PlantAlias::getPlantId, plantId)
                .orderByAsc(PlantAlias::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void add(String plantId, PlantAliasDTO dto) {
        ensurePlantExists(plantId);
        AliasValue value = validate(dto);
        ensureUnique(plantId, value.normalizedAlias, null);
        save(PlantAlias.builder()
                .plantId(plantId)
                .alias(value.alias)
                .normalizedAlias(value.normalizedAlias)
                .aliasType(value.aliasType)
                .enabled(dto.getEnabled() == null || dto.getEnabled())
                .build());
    }

    @Override
    @Transactional
    public void update(String plantId, String aliasId, PlantAliasDTO dto) {
        PlantAlias alias = getPlantAlias(plantId, aliasId);
        AliasValue value = validate(dto);
        ensureUnique(plantId, value.normalizedAlias, aliasId);
        alias.setAlias(value.alias);
        alias.setNormalizedAlias(value.normalizedAlias);
        alias.setAliasType(value.aliasType);
        if (dto.getEnabled() != null) {
            alias.setEnabled(dto.getEnabled());
        }
        updateById(alias);
    }

    @Override
    @Transactional
    public void delete(String plantId, String aliasId) {
        removeById(getPlantAlias(plantId, aliasId).getId());
    }

    @Override
    @Transactional
    public void deleteByPlantIds(Collection<String> plantIds) {
        if (plantIds == null || plantIds.isEmpty()) {
            return;
        }
        remove(new LambdaQueryWrapper<PlantAlias>().in(PlantAlias::getPlantId, plantIds));
    }

    private PlantAlias getPlantAlias(String plantId, String aliasId) {
        PlantAlias alias = getById(aliasId);
        if (alias == null || !plantId.equals(alias.getPlantId())) {
            throw new ApiException("植物别名不存在");
        }
        return alias;
    }

    private void ensurePlantExists(String plantId) {
        if (plantId == null || plantId.trim().isEmpty() || plantsMapper.selectById(plantId) == null) {
            throw new ApiException("植物不存在");
        }
    }

    private void ensureUnique(String plantId, String normalizedAlias, String aliasId) {
        LambdaQueryWrapper<PlantAlias> query = new LambdaQueryWrapper<PlantAlias>()
                .eq(PlantAlias::getPlantId, plantId)
                .eq(PlantAlias::getNormalizedAlias, normalizedAlias);
        if (aliasId != null) {
            query.ne(PlantAlias::getId, aliasId);
        }
        if (count(query) > 0) {
            throw new ApiException("该植物已存在相同别名");
        }
    }

    private AliasValue validate(PlantAliasDTO dto) {
        if (dto == null) {
            throw new ApiException("别名不能为空");
        }
        String alias = dto.getAlias() == null ? "" : dto.getAlias().trim();
        if (alias.isEmpty() || alias.length() > 100) {
            throw new ApiException("别名长度需为 1 至 100 个字符");
        }
        String normalizedAlias = Normalizer.normalize(alias, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (normalizedAlias.isEmpty() || normalizedAlias.length() > 100) {
            throw new ApiException("别名格式不合法");
        }
        String aliasType = dto.getAliasType() == null ? "" : dto.getAliasType().trim().toUpperCase(Locale.ROOT);
        if (!ALIAS_TYPES.contains(aliasType)) {
            throw new ApiException("别名类型不合法");
        }
        return new AliasValue(alias, normalizedAlias, aliasType);
    }

    private PlantAliasVO toVO(PlantAlias alias) {
        return new PlantAliasVO(alias.getId(), alias.getAlias(), alias.getAliasType(), alias.getEnabled());
    }

    private static class AliasValue {
        private final String alias;
        private final String normalizedAlias;
        private final String aliasType;

        private AliasValue(String alias, String normalizedAlias, String aliasType) {
            this.alias = alias;
            this.normalizedAlias = normalizedAlias;
            this.aliasType = aliasType;
        }
    }
}
