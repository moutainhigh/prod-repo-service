package org.hrds.rdupm.nexus.app.service.impl;

import com.github.pagehelper.PageInfo;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.apache.commons.collections.CollectionUtils;
import org.hrds.rdupm.nexus.api.dto.NexusComponentGuideDTO;
import org.hrds.rdupm.nexus.app.service.NexusComponentService;
import org.hrds.rdupm.nexus.app.service.NexusServerConfigService;
import org.hrds.rdupm.nexus.client.nexus.NexusClient;
import org.hrds.rdupm.nexus.client.nexus.exception.NexusResponseException;
import org.hrds.rdupm.nexus.client.nexus.model.*;
import org.hrds.rdupm.nexus.domain.entity.NexusRepository;
import org.hrds.rdupm.nexus.domain.repository.NexusRepositoryRepository;
import org.hrds.rdupm.nexus.infra.constant.NexusMessageConstants;
import org.hrds.rdupm.nexus.infra.util.PageConvertUtils;
import org.hrds.rdupm.nexus.infra.util.VelocityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 制品库_nexus 包信息应用服务默认实现
 * @author weisen.yang@hand-china.com 2020/4/2
 */
@Component
public class NexusComponentServiceImpl implements NexusComponentService {
	private static final Logger logger = LoggerFactory.getLogger(NexusComponentServiceImpl.class);


	@Autowired
	private NexusClient nexusClient;
	@Autowired
	private NexusServerConfigService configService;
	@Autowired
	private NexusRepositoryRepository nexusRepositoryRepository;

	@Override
	public PageInfo<NexusServerComponentInfo> listComponents(Long organizationId, Long projectId, Boolean deleteFlag,
															 NexusComponentQuery componentQuery, PageRequest pageRequest) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);

		// 查询所有数据
		List<NexusServerComponentInfo> componentInfoList = nexusClient.getComponentsApi().searchComponentInfo(componentQuery);
		// 分页
		PageInfo<NexusServerComponentInfo> componentInfoPageInfo = PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), componentInfoList);

		if (deleteFlag && projectId != null) {
			List<String> proRepoList = nexusRepositoryRepository.getRepositoryByProject(projectId);
			componentInfoPageInfo.getList().forEach(nexusServerComponentInfo -> {
				if (proRepoList.contains(nexusServerComponentInfo.getRepository())) {
					nexusServerComponentInfo.setDeleteFlag(true);
				} else {
					nexusServerComponentInfo.setDeleteFlag(false);
				}
				nexusServerComponentInfo.getComponents().forEach(nexusServerComponent -> {
					nexusServerComponent.setDeleteFlag(nexusServerComponentInfo.getDeleteFlag());
				});

			});

		}
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return componentInfoPageInfo;
	}

	@Override
	public void deleteComponents(Long organizationId, Long projectId, String repositoryName, List<String> componentIds) {
		NexusRepository query = new NexusRepository();
		query.setProjectId(projectId);
		query.setNeRepositoryName(repositoryName);
		NexusRepository nexusRepository = nexusRepositoryRepository.selectOne(query);
		if (nexusRepository == null) {
			throw new CommonException(NexusMessageConstants.NEXUS_NOT_DELETE_COMPONENT);
		}
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		componentIds.forEach(componentId -> {
			try {
				nexusClient.getComponentsApi().deleteComponent(componentId);
			} catch (NexusResponseException e) {
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					// 删除数据没找到，直接跳过
					logger.error("delete component error, 404 not found: {}", componentId);
				} else {
					throw e;
				}
			}
		});

		// remove配置信息
		nexusClient.removeNexusServerInfo();
	}

	@Override
	public void componentsUpload(Long organizationId, Long projectId,
								 NexusServerComponentUpload componentUpload,
								 MultipartFile assetJar, MultipartFile assetPom) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		try (
				InputStream assetJarStream = assetJar != null ? assetJar.getInputStream() : null;
				InputStream assetPomStream = assetPom != null ? assetPom.getInputStream() : null
		) {
			List<NexusServerAssetUpload> assetUploadList = new ArrayList<>();
			if (assetJarStream != null) {
				NexusServerAssetUpload assetUpload = new NexusServerAssetUpload();
				assetUpload.setAssetName(new InputStreamResource(assetJarStream));
				assetUpload.setExtension(NexusServerAssetUpload.JAR);
				assetUploadList.add(assetUpload);
			}
			if (assetPomStream != null) {
				NexusServerAssetUpload assetUpload = new NexusServerAssetUpload();
				assetUpload.setAssetName(new InputStreamResource(assetPomStream));
				assetUpload.setExtension(NexusServerAssetUpload.POM);
				assetUploadList.add(assetUpload);
			}
			componentUpload.setAssetUploads(assetUploadList);
			nexusClient.getComponentsApi().createMavenComponent(componentUpload);
		} catch (IOException e) {
			logger.error("上传jar包错误", e);
			throw new CommonException(e.getMessage());
		}

		// remove配置信息
		nexusClient.removeNexusServerInfo();
	}

	@Override
	public NexusComponentGuideDTO componentGuide(NexusServerComponentInfo componentInfo) {
		Map<String, Object> map = new HashMap<>(16);
		map.put("groupId", componentInfo.getGroup());
		map.put("name", componentInfo.getName());
		map.put("version", componentInfo.getVersion());
		NexusComponentGuideDTO componentGuideDTO = new NexusComponentGuideDTO();
		componentGuideDTO.setPullPomDep(VelocityUtils.getJsonString(map, VelocityUtils.POM_DEPENDENCY_FILE_NAME));
		return componentGuideDTO;
	}
}