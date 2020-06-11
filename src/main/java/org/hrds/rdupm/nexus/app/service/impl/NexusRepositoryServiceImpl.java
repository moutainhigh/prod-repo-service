package org.hrds.rdupm.nexus.app.service.impl;

import io.choerodon.core.domain.Page;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.producer.StartSagaBuilder;
import io.choerodon.asgard.saga.producer.TransactionalProducer;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.domain.AuditDomain;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hrds.rdupm.harbor.app.service.C7nBaseService;
import org.hrds.rdupm.harbor.infra.feign.dto.UserDTO;
import org.hrds.rdupm.nexus.api.dto.*;
import org.hrds.rdupm.nexus.app.eventhandler.constants.NexusSagaConstants;
import org.hrds.rdupm.nexus.app.eventhandler.payload.NexusRepositoryDeletePayload;
import org.hrds.rdupm.nexus.app.service.NexusAuthService;
import org.hrds.rdupm.nexus.app.service.NexusRepositoryService;
import org.hrds.rdupm.nexus.app.service.NexusServerConfigService;
import org.hrds.rdupm.nexus.client.nexus.NexusClient;
import org.hrds.rdupm.nexus.client.nexus.constant.NexusApiConstants;
import org.hrds.rdupm.nexus.client.nexus.model.*;
import org.hrds.rdupm.nexus.domain.entity.*;
import org.hrds.rdupm.nexus.domain.repository.*;
import org.hrds.rdupm.nexus.infra.constant.NexusConstants;
import org.hrds.rdupm.nexus.infra.constant.NexusMessageConstants;
import org.hrds.rdupm.nexus.infra.feign.BaseServiceFeignClient;
import org.hrds.rdupm.nexus.infra.feign.vo.ProjectVO;
import org.hrds.rdupm.nexus.infra.util.PageConvertUtils;
import org.hrds.rdupm.util.DESEncryptUtil;
import org.hzero.core.base.AopProxy;
import org.hzero.core.base.BaseConstants;
import org.hzero.mybatis.domian.Condition;
import org.hzero.mybatis.util.Sqls;
import org.omg.CORBA.COMM_FAILURE;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 制品库_nexus仓库信息表应用服务默认实现
 *
 * @author weisen.yang@hand-china.com 2020-03-27 11:43:00
 */
@Service
public class NexusRepositoryServiceImpl implements NexusRepositoryService, AopProxy<NexusRepositoryService> {

	@Autowired
	private NexusRepositoryRepository nexusRepositoryRepository;
	@Autowired
	private NexusRoleRepository nexusRoleRepository;
	@Autowired
	private NexusUserRepository nexusUserRepository;
	@Autowired
	private NexusClient nexusClient;
	@Autowired
	private NexusServerConfigService configService;
	@Autowired
	private BaseServiceFeignClient baseServiceFeignClient;
	@Autowired
	private TransactionalProducer producer;
	@Autowired
	private NexusPushRepository nexusPushRepository;
	@Autowired
	private NexusAuthRepository nexusAuthRepository;
	@Autowired
	private NexusAuthService nexusAuthService;
	@Autowired
	private C7nBaseService c7nBaseService;

	@Override
	public NexusRepositoryDTO getRepo(Long organizationId, Long projectId, Long repositoryId) {
		configService.setNexusInfo(nexusClient);

		NexusRepository query = new NexusRepository();
		query.setRepositoryId(repositoryId);
		query.setOrganizationId(organizationId);
		query.setProjectId(projectId);

		NexusRepository nexusRepository = nexusRepositoryRepository.selectOne(query);
		if (nexusRepository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}
		NexusServerRepository nexusServerRepository = nexusClient.getRepositoryApi().getRepositoryByName(nexusRepository.getNeRepositoryName());
		if (nexusServerRepository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}
		NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
		nexusRepositoryDTO.convert(nexusRepository, nexusServerRepository);
		nexusClient.removeNexusServerInfo();
		return nexusRepositoryDTO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Saga(code = NexusSagaConstants.NexusMavenRepoCreate.MAVEN_REPO_CREATE,
			description = "创建maven仓库",
			inputSchemaClass = NexusRepositoryCreateDTO.class)
	public NexusRepositoryCreateDTO createRepo(Long organizationId, Long projectId, NexusRepositoryCreateDTO nexusRepoCreateDTO) {

		// 步骤
		// 1. 更新数据库数据
		// 2. 创建仓库
		// 3. 创建仓库默认拉取角色
		// 4. 创建仓库默认用户，默认赋予角色，上述创建的角色
		// 5. 是否允许匿名
		//     允许，赋予匿名用户权限，如： nx-repository-view-maven2-[仓库名]-read   nx-repository-view-maven2-[仓库名]-browse
		//     不允许，去除匿名用户权限，如：nx-repository-view-maven2-[仓库名]-read   nx-repository-view-maven2-[仓库名]-browse

		// 参数校验
		nexusRepoCreateDTO.validParam(baseServiceFeignClient, true);

		NexusServerConfig serverConfig = configService.setNexusInfo(nexusClient);


		if (nexusClient.getRepositoryApi().repositoryExists(nexusRepoCreateDTO.getName())) {
			throw new CommonException(NexusApiConstants.ErrorMessage.REPO_NAME_EXIST);
		}

		// 1. 数据库数据更新
		// 仓库
		NexusRepository nexusRepository = new NexusRepository();
		nexusRepository.setConfigId(serverConfig.getConfigId());
		nexusRepository.setNeRepositoryName(nexusRepoCreateDTO.getName());
		nexusRepository.setOrganizationId(organizationId);
		nexusRepository.setProjectId(projectId);
		nexusRepository.setAllowAnonymous(nexusRepoCreateDTO.getAllowAnonymous());
		nexusRepository.setRepoType(nexusRepoCreateDTO.getRepoType());
		nexusRepository.setEnableFlag(NexusConstants.Flag.Y);
		nexusRepositoryRepository.insertSelective(nexusRepository);

		// 角色
		NexusServerRole nexusServerRole = new NexusServerRole();
		// 发布角色
		nexusServerRole.createDefPushRole(nexusRepoCreateDTO.getName(), true, null, nexusRepoCreateDTO.getFormat());
		// 拉取角色
		NexusServerRole pullNexusServerRole = new NexusServerRole();
		pullNexusServerRole.createDefPullRole(nexusRepoCreateDTO.getName(), null, nexusRepoCreateDTO.getFormat());

		NexusRole nexusRole = new NexusRole();
		nexusRole.setRepositoryId(nexusRepository.getRepositoryId());
		nexusRole.setNePullRoleId(pullNexusServerRole.getId());
		nexusRole.setNeRoleId(nexusServerRole.getId());
		nexusRoleRepository.insertSelective(nexusRole);

		// 用户
		// 发布用户
		NexusServerUser nexusServerUser = new NexusServerUser();
		nexusServerUser.createDefPushUser(nexusRepoCreateDTO.getName(), nexusServerRole.getId(), null);
		// 拉取用户
		NexusServerUser pullNexusServerUser = new NexusServerUser();
		pullNexusServerUser.createDefPullUser(nexusRepoCreateDTO.getName(), pullNexusServerRole.getId(), null);

		NexusUser nexusUser = new NexusUser();
		nexusUser.setRepositoryId(nexusRepository.getRepositoryId());
		nexusUser.setNeUserId(nexusServerUser.getUserId());
		nexusUser.setNeUserPassword(DESEncryptUtil.encode(nexusServerUser.getPassword()));
		nexusUser.setNePullUserId(pullNexusServerUser.getUserId());
		nexusUser.setNePullUserPassword(DESEncryptUtil.encode(pullNexusServerUser.getPassword()));
		nexusUserRepository.insertSelective(nexusUser);

		// 创建用户权限
		List<NexusAuth> nexusAuthList = nexusAuthService.createNexusAuth(Collections.singletonList(DetailsHelper.getUserDetails().getUserId()), nexusRepository.getRepositoryId(), NexusConstants.NexusRoleEnum.PROJECT_ADMIN.getRoleCode());
		nexusRepoCreateDTO.setNexusAuthList(nexusAuthList);

		producer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(NexusSagaConstants.NexusMavenRepoCreate.MAVEN_REPO_CREATE)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("mavenRepo")
						.withSourceId(projectId),
				builder -> {
					builder.withPayloadAndSerialize(nexusRepoCreateDTO)
							.withRefId(String.valueOf(nexusRepository.getRepositoryId()))
							.withSourceId(projectId);
				});


		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return nexusRepoCreateDTO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Saga(code = NexusSagaConstants.NexusRepoDistribute.SITE_NEXUS_REPO_DISTRIBUTE,
			description = "平台层-nexus仓库分配",
			inputSchemaClass = NexusRepository.class)
	public NexusRepositoryCreateDTO repoDistribute(NexusRepositoryCreateDTO nexusRepoCreateDTO) {

		// 步骤
		// 1. 更新数据库数据
		// 2. 创建仓库默认角色，赋予权限：nx-repository-view-[format]-[仓库名]-*; 创建仓库拉取角色
		// 3. 创建仓库拉取用户rdupm_nexus_user，分配仓库拉取角色（用于不允许匿名拉取时的pull操作）
		// 4. 根据传入的仓库管理员，创建制品库用户rdupm_prod_user，创建nexus用户并赋予默认角色
		// 5. 是否允许匿名
		//     允许，赋予匿名用户权限：nx-repository-view-[format]-[仓库名]-read   nx-repository-view-[format]-[仓库名]-browse
		//     不允许，去除匿名用户权限：nx-repository-view-[format]-[仓库名]-read   nx-repository-view-[format]-[仓库名]-browse

		// 参数校验
		nexusRepoCreateDTO.validParam(baseServiceFeignClient, true);

		NexusServerConfig serverConfig = configService.setNexusInfo(nexusClient);

		if (!nexusClient.getRepositoryApi().repositoryExists(nexusRepoCreateDTO.getName())){
			throw new CommonException(NexusApiConstants.ErrorMessage.RESOURCE_NOT_EXIST);
		}

		// 1. 数据库数据更新
		// 仓库
		Long adminId = nexusRepoCreateDTO.getDistributeRepoAdminId();
		NexusRepository insertRepo = new NexusRepository();
		insertRepo.setCreatedBy(adminId);
		insertRepo.setConfigId(serverConfig.getConfigId());
		insertRepo.setNeRepositoryName(nexusRepoCreateDTO.getName());
		insertRepo.setOrganizationId(nexusRepoCreateDTO.getOrganizationId());
		insertRepo.setProjectId(nexusRepoCreateDTO.getProjectId());
		insertRepo.setAllowAnonymous(nexusRepoCreateDTO.getAllowAnonymous());
		insertRepo.setRepoType(nexusRepoCreateDTO.getRepoType());
		nexusRepositoryRepository.distributeRepoInsert(insertRepo);
		List<NexusRepository> nexusRepositories = nexusRepositoryRepository.selectByCondition(Condition.builder(NexusRepository.class).andWhere(Sqls.custom().andEqualTo(NexusRepository.FIELD_NE_REPOSITORY_NAME, insertRepo.getNeRepositoryName())).build());
		NexusRepository nexusRepository = nexusRepositories.get(0);

		// 角色
		NexusServerRole nexusServerRole = new NexusServerRole();
		// 发布角色
		nexusServerRole.createDefPushRole(nexusRepoCreateDTO.getName(), true, null, nexusRepoCreateDTO.getRepoType());
		// 拉取角色
		NexusServerRole pullNexusServerRole = new NexusServerRole();
		pullNexusServerRole.createDefPullRole(nexusRepoCreateDTO.getName(), null, nexusRepoCreateDTO.getRepoType());

		NexusRole nexusRole = new NexusRole();
		nexusRole.setRepositoryId(nexusRepository.getRepositoryId());
		nexusRole.setNePullRoleId(pullNexusServerRole.getId());
		nexusRole.setNeRoleId(nexusServerRole.getId());
		nexusRoleRepository.insertSelective(nexusRole);

		// 拉取用户
		NexusServerUser pullNexusServerUser = new NexusServerUser();
		pullNexusServerUser.createDefPullUser(nexusRepoCreateDTO.getName(), pullNexusServerRole.getId(), null);

		NexusUser nexusUser = new NexusUser();
		nexusUser.setRepositoryId(nexusRepository.getRepositoryId());
		nexusUser.setNePullUserId(pullNexusServerUser.getUserId());
		nexusUser.setNePullUserPassword(DESEncryptUtil.encode(pullNexusServerUser.getPassword()));
		nexusUserRepository.insertSelective(nexusUser);

		// 获取项目“项目管理员”角色人员
		// Long projectId = nexusRepoCreateDTO.getProjectId();
		// List<UserDTO> ownerUsers = c7nBaseService.listProjectOwnerUsers(projectId);
		// List<Long> userIds = ownerUsers.stream().map(UserDTO::getId).collect(Collectors.toList());
		// 创建用户权限
		List<NexusAuth> nexusAuthList = nexusAuthService.createNexusAuth(Collections.singletonList(adminId),
				nexusRepository.getRepositoryId(), NexusConstants.NexusRoleEnum.PROJECT_ADMIN.getRoleCode());
		nexusRepoCreateDTO.setNexusAuthList(nexusAuthList);
		nexusRepository.setNexusAuthList(nexusAuthList);

		producer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(NexusSagaConstants.NexusRepoDistribute.SITE_NEXUS_REPO_DISTRIBUTE)
						.withLevel(ResourceLevel.SITE)
						.withRefType("nexusRepo"),
				builder -> builder.withPayloadAndSerialize(nexusRepository)
						.withRefId(String.valueOf(nexusRepository.getRepositoryId())));

		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return nexusRepoCreateDTO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Saga(code = NexusSagaConstants.NexusMavenRepoUpdate.MAVEN_REPO_UPDATE,
			description = "更新maven仓库",
			inputSchemaClass = NexusRepositoryCreateDTO.class)
	public NexusRepositoryCreateDTO updateRepo(Long organizationId, Long projectId, Long repositoryId, NexusRepositoryCreateDTO nexusRepoCreateDTO) {

		// 参数校验
		nexusRepoCreateDTO.validParam(baseServiceFeignClient, false);

		// 设置并返回当前nexus服务信息
		NexusServerConfig serverConfig = configService.setNexusInfo(nexusClient);

		NexusRepository nexusRepository = nexusRepositoryRepository.selectByPrimaryKey(repositoryId);
		if (nexusRepository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}
		if (!nexusRepository.getProjectId().equals(projectId)) {
			throw new CommonException(NexusMessageConstants.NEXUS_MAVEN_REPO_NOT_CHANGE_OTHER_PRO);
		}
		if (!nexusRepository.getAllowAnonymous().equals(nexusRepoCreateDTO.getAllowAnonymous())) {
			nexusRepository.setAllowAnonymous(nexusRepoCreateDTO.getAllowAnonymous());
			nexusRepositoryRepository.updateOptional(nexusRepository, NexusRepository.FIELD_ALLOW_ANONYMOUS);
		}

		producer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(NexusSagaConstants.NexusMavenRepoUpdate.MAVEN_REPO_UPDATE)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("updateMavenRepo")
						.withSourceId(projectId),
				builder -> {
					builder.withPayloadAndSerialize(nexusRepoCreateDTO)
							.withRefId(String.valueOf(nexusRepository.getRepositoryId()))
							.withSourceId(projectId);
				});

		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return nexusRepoCreateDTO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Saga(code = NexusSagaConstants.NexusMavenRepoDelete.MAVEN_REPO_DELETE,
			description = "删除maven仓库",
			inputSchemaClass = NexusRepositoryDeletePayload.class)
	public void deleteRepo(Long organizationId, Long projectId, Long repositoryId) {
		// 仓库
		NexusRepository nexusRepository = nexusRepositoryRepository.selectByPrimaryKey(repositoryId);
		if (nexusRepository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}
		// 角色
		NexusRole roleQuery = new NexusRole();
		roleQuery.setRepositoryId(repositoryId);
		NexusRole nexusRole = nexusRoleRepository.selectOne(roleQuery);
		// 用户
		NexusUser userQuery = new NexusUser();
		userQuery.setRepositoryId(repositoryId);
		NexusUser nexusUser = nexusUserRepository.selectOne(userQuery);

		// 权限
		NexusAuth authQuery = new NexusAuth();
		authQuery.setRepositoryId(repositoryId);
		List<NexusAuth> nexusAuthList = nexusAuthRepository.select(authQuery);

		// 数据库数据删除
		nexusRepositoryRepository.deleteByPrimaryKey(nexusRepository);
		nexusRoleRepository.deleteByPrimaryKey(nexusRole);
		nexusUserRepository.deleteByPrimaryKey(nexusUser);
		nexusAuthRepository.batchDeleteByPrimaryKey(nexusAuthList);


		NexusRepositoryDeletePayload deletePayload = new NexusRepositoryDeletePayload();
		deletePayload.setNexusRepository(nexusRepository);
		deletePayload.setNexusRole(nexusRole);
		deletePayload.setNexusUser(nexusUser);
		producer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(NexusSagaConstants.NexusMavenRepoDelete.MAVEN_REPO_DELETE)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("deleteMavenRepo")
						.withSourceId(projectId),
				builder -> {
					builder.withPayloadAndSerialize(deletePayload)
							.withRefId(String.valueOf(nexusRepository.getRepositoryId()))
							.withSourceId(projectId);
				});

	}

	@Override
	public Page<NexusRepositoryDTO> listRepo(PageRequest pageRequest, NexusRepositoryQueryDTO queryDTO, String queryData) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		List<NexusRepositoryDTO> resultAll = this.queryRepo(queryDTO, queryData, this.convertRepoTypeToFormat(queryDTO.getRepoType()));
		if (CollectionUtils.isEmpty(resultAll)) {
			return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
		}
		// remove配置信息
		nexusClient.removeNexusServerInfo();

		return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
	}

	@Override
	public Page<NexusRepositoryDTO> listNexusRepo(PageRequest pageRequest, NexusRepositoryQueryDTO queryDTO) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		Page<NexusRepositoryDTO> pageResult = this.queryNexusRepo(queryDTO, pageRequest);
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return pageResult;
	}

	private Page<NexusRepositoryDTO> queryNexusRepo(NexusRepositoryQueryDTO queryDTO, PageRequest pageRequest) {
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(queryDTO.getRepoType()));
		List<NexusRepositoryDTO> resultAll = new ArrayList<>();
		if (CollectionUtils.isEmpty(nexusServerRepositoryList)) {
			return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
		}

		// 查询所有的nexus仓库，标识已经关联项目的仓库
		this.mavenRepoAll(nexusServerRepositoryList, queryDTO, resultAll);
		if (CollectionUtils.isEmpty(resultAll)) {
			return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
		}
		// 项目名称查询
		Set<Long> projectIdSet = resultAll.stream().map(NexusRepositoryDTO::getProjectId).collect(Collectors.toSet());
		List<ProjectVO> projectVOList = baseServiceFeignClient.queryByIds(projectIdSet);
		Map<Long, ProjectVO> projectVOMap = projectVOList.stream().collect(Collectors.toMap(ProjectVO::getId, a -> a, (k1, k2) -> k1));
		resultAll.forEach(nexusRepositoryDTO -> {
			ProjectVO projectVO = projectVOMap.get(nexusRepositoryDTO.getProjectId());
			if (projectVO != null) {
				nexusRepositoryDTO.setProjectName(projectVO.getName());
				nexusRepositoryDTO.setProjectImgUrl(projectVO.getImageUrl());
			}
		});

		// 查询参数
		if (queryDTO.getRepositoryName() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getName().toLowerCase().contains(queryDTO.getRepositoryName().toLowerCase())).collect(Collectors.toList());
		}
		if (queryDTO.getDistributedQueryFlag() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO -> {
				if (Objects.nonNull(queryDTO.getDistributedQueryFlag())) {
					return Objects.equals(queryDTO.getDistributedQueryFlag(), BaseConstants.Flag.NO) ? Objects.isNull(nexusRepositoryDTO.getRepositoryId()) :
							Objects.nonNull(nexusRepositoryDTO.getRepositoryId());
				}
				return true;
			}).collect(Collectors.toList());
		}
		if (queryDTO.getType() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getType().toLowerCase().contains(queryDTO.getType().toLowerCase())).collect(Collectors.toList());
		}
		if (queryDTO.getVersionPolicy() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getVersionPolicy() != null && nexusRepositoryDTO.getVersionPolicy().toLowerCase().contains(queryDTO.getVersionPolicy().toLowerCase())).collect(Collectors.toList());
		}
		resultAll.sort(new NexusRepositoryDTO());
		return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
	}


	@Override
	public List<NexusRepositoryDTO> listRepoAll(NexusRepositoryQueryDTO queryDTO, String queryData) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		List<NexusRepositoryDTO> resultAll = this.queryRepo(queryDTO, queryData, this.convertRepoTypeToFormat(queryDTO.getRepoType()));
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return resultAll;
	}

	/**
	 * 查询所有仓库-信息处理
	 * @param nexusServerRepositoryList nexus服务仓库信息
	 * @param queryDTO 查询参数
	 * @param resultAll 放回结果
	 */
	private void mavenRepoAll(List<NexusServerRepository> nexusServerRepositoryList, NexusRepositoryQueryDTO queryDTO, List<NexusRepositoryDTO> resultAll){
		// 所有项目仓库数据
		NexusRepository query = new NexusRepository();
		if (queryDTO.getRepoType() != null) {
			query.setRepoType(queryDTO.getRepoType());
		}
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.select(query);
		Map<String, NexusRepository> nexusRepositoryMap = nexusRepositoryList.stream().collect(Collectors.toMap(NexusRepository::getNeRepositoryName, a -> a, (k1, k2) -> k1));
		this.setUserInfo(nexusRepositoryList);
		nexusServerRepositoryList.forEach(serverRepository -> {
			NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
			nexusRepositoryDTO.convert(nexusRepositoryMap.get(serverRepository.getName()), serverRepository);
			resultAll.add(nexusRepositoryDTO);
		});

	}

	/**
	 * 设置用户信息
	 * @param nexusRepositoryList 仓库列表
	 */
	private void setUserInfo(List<NexusRepository> nexusRepositoryList){
		//创建人ID去重，并获得创建人详细信息
		Set<Long> userIdSet = nexusRepositoryList.stream().map(AuditDomain::getCreatedBy).collect(Collectors.toSet());
		if (CollectionUtils.isNotEmpty(userIdSet)) {
			List<UserDTO> userDTOList = baseServiceFeignClient.listUsersByIds(userIdSet.toArray(new Long[userIdSet.size()]),true);
			Map<Long,UserDTO> userDtoMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId,dto->dto));
			if (CollectionUtils.isNotEmpty(userDTOList)) {
				nexusRepositoryList.forEach(repository -> {
					//设置创建人登录名、真实名称、创建人头像
					UserDTO userDTO = userDtoMap.get(repository.getCreatedBy());
					if(userDTO != null){
						repository.setCreatorImageUrl(userDTO.getImageUrl());
						repository.setCreatorLoginName(userDTO.getLoginName());
						repository.setCreatorRealName(userDTO.getRealName());
					}
				});
			}
		}
	}

	/**
	 * 查询某个组织仓库-信息处理
	 * @param nexusServerRepositoryList nexus服务仓库信息
	 * @param queryDTO 查询参数
	 * @param resultAll 放回结果
	 */
	private void mavenRepoOrg(List<NexusServerRepository> nexusServerRepositoryList, NexusRepositoryQueryDTO queryDTO, List<NexusRepositoryDTO> resultAll){

		Map<String, NexusServerRepository> nexusServerRepositoryMap = nexusServerRepositoryList.stream().collect(Collectors.toMap(NexusServerRepository::getName, a -> a, (k1, k2) -> k1));

		// 查询某个组织项目数据
		Condition.Builder builder = Condition.builder(NexusRepository.class)
				.where(Sqls.custom()
						.andEqualTo(NexusRepository.FIELD_ORGANIZATION_ID, queryDTO.getOrganizationId()));
		if (queryDTO.getRepoType() != null) {
			builder.where(Sqls.custom()
					.andEqualTo(NexusRepository.FIELD_REPO_TYPE, queryDTO.getRepoType()));
		}
		Condition condition = builder.build();
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.selectByCondition(condition);

		this.mavenRepoConvert(resultAll, nexusRepositoryList, nexusServerRepositoryMap);
	}

	/**
	 * 查询排除当前项目创建或关联的仓库后的仓库-信息处理
	 * @param nexusServerRepositoryList nexus服务仓库信息
	 * @param queryDTO 查询参数
	 * @param resultAll 放回结果
	 */
	private void mavenRepoExcludeProject(List<NexusServerRepository> nexusServerRepositoryList, NexusRepositoryQueryDTO queryDTO, List<NexusRepositoryDTO> resultAll){

		// 所有项目仓库数据
		NexusRepository query = new NexusRepository();
		if (queryDTO.getRepoType() != null) {
			query.setRepoType(queryDTO.getRepoType());
		}
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.select(query);
		Map<String, NexusRepository> nexusRepositoryMap = nexusRepositoryList.stream().collect(Collectors.toMap(NexusRepository::getNeRepositoryName, a -> a, (k1, k2) -> k1));
		// 设置用户信息
		this.setUserInfo(nexusRepositoryList);
		// 过滤数据，排除当前项目的
		Set<String> currentProject = nexusRepositoryList.stream().filter(nexusRepository -> nexusRepository.getProjectId().equals(queryDTO.getProjectId()))
				.map(NexusRepository::getNeRepositoryName).collect(Collectors.toSet());
		nexusServerRepositoryList = nexusServerRepositoryList.stream().filter(nexusRoleRepository ->
				!currentProject.contains(nexusRoleRepository.getName())
		).collect(Collectors.toList());

		nexusServerRepositoryList.forEach(serverRepository -> {
			NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
			nexusRepositoryDTO.convert(nexusRepositoryMap.get(serverRepository.getName()), serverRepository);
			resultAll.add(nexusRepositoryDTO);
		});
	}

	/**
	 * 查询当前项目下创建或关联的仓库-信息处理
	 * @param nexusServerRepositoryList nexus服务仓库信息
	 * @param queryDTO 查询参数
	 * @param resultAll 放回结果
	 */
	private void mavenRepoProject(List<NexusServerRepository> nexusServerRepositoryList, NexusRepositoryQueryDTO queryDTO, List<NexusRepositoryDTO> resultAll){

		Map<String, NexusServerRepository> nexusServerRepositoryMap = nexusServerRepositoryList.stream().collect(Collectors.toMap(NexusServerRepository::getName, a -> a, (k1, k2) -> k1));

		// 查询某个项目项目数据
		Condition.Builder builder = Condition.builder(NexusRepository.class)
				.where(Sqls.custom()
						.andEqualTo(NexusRepository.FIELD_PROJECT_ID, queryDTO.getProjectId()));
		if (queryDTO.getRepoType() != null) {
			builder.where(Sqls.custom()
					.andEqualTo(NexusRepository.FIELD_REPO_TYPE, queryDTO.getRepoType()));
		}
		Condition condition = builder.build();
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.selectByCondition(condition);

		this.mavenRepoConvert(resultAll, nexusRepositoryList, nexusServerRepositoryMap);


	}
	private void mavenRepoConvert(List<NexusRepositoryDTO> resultAll,
								  List<NexusRepository> nexusRepositoryList,
								  Map<String, NexusServerRepository> nexusServerRepositoryMap){
		// 设置用户信息
		this.setUserInfo(nexusRepositoryList);
		nexusRepositoryList.forEach(repository -> {
			NexusServerRepository nexusServerRepository = nexusServerRepositoryMap.get(repository.getNeRepositoryName());
			NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
			nexusRepositoryDTO.convert(repository, nexusServerRepository);
			resultAll.add(nexusRepositoryDTO);

		});
	}


	@Override
	public List<NexusServerBlobStore> listMavenRepoBlob() {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		List<NexusServerBlobStore> blobStoreList = nexusClient.getBlobStoreApi().getBlobStore();
		blobStoreList.forEach(blobStore -> {
			blobStore.setBlobCount(null);
			blobStore.setTotalSizeInBytes(null);
			blobStore.setAvailableSpaceInBytes(null);
		});


		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return blobStoreList;
	}

	@Override
	public List<NexusRepositoryDTO> listRepoNameAll(Long projectId, Boolean excludeRelated, String repoType) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);

		// 所有nexus服务仓库数据
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(repoType));
		if (CollectionUtils.isEmpty(nexusServerRepositoryList)) {
			return new ArrayList<>();
		}
		// 所有项目仓库数据
		List<String> repositoryNameList = new ArrayList<>();
		if (excludeRelated) {
			repositoryNameList = nexusRepositoryRepository.getRepositoryByProject(null, repoType);
		}
		List<String> finalRepositoryNameList = repositoryNameList;


		List<NexusRepositoryDTO> resultAll = new ArrayList<>();
		nexusServerRepositoryList.forEach(serverRepository -> {
			if (!finalRepositoryNameList.contains(serverRepository.getName())) {
				NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
				nexusRepositoryDTO.setName(serverRepository.getName());
				resultAll.add(nexusRepositoryDTO);
			}
		});

		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return resultAll;
	}

	@Override
	public List<NexusRepositoryDTO> listRepoName(NexusRepository query, String repoType) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);

		// nexus服务，仓库数据
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(repoType));
		if (CollectionUtils.isEmpty(nexusServerRepositoryList)) {
			return new ArrayList<>();
		}
		List<String> serverRepositoryNameList = nexusServerRepositoryList.stream().map(NexusServerRepository::getName).collect(Collectors.toList());

		// 仓库数据
		query.setRepoType(repoType);
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.listRepositoryByProject(query);
		List<String> repositoryNameList = nexusRepositoryList.stream().map(NexusRepository::getNeRepositoryName).collect(Collectors.toList());
		if (CollectionUtils.isEmpty(repositoryNameList)) {
			return new ArrayList<>();
		}

		List<NexusRepositoryDTO> resultAll = new ArrayList<>();
		repositoryNameList.forEach(repositoryName -> {
			if (serverRepositoryNameList.contains(repositoryName)) {
				NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
				nexusRepositoryDTO.setName(repositoryName);
				resultAll.add(nexusRepositoryDTO);
			}
		});
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return resultAll;
	}

	@Override
	public List<NexusRepositoryDTO> listComponentRepo(Long projectId, String repoType) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);

		// nexus服务，仓库数据
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(repoType));
		if (CollectionUtils.isEmpty(nexusServerRepositoryList)) {
			return new ArrayList<>();
		}
		Map<String, NexusServerRepository> nexusServerRepositoryMap = nexusServerRepositoryList.stream().collect(Collectors.toMap(NexusServerRepository::getName, a -> a, (k1, k2) -> k1));


		// 当前项目仓库数据
		List<String> repositoryNameList = nexusRepositoryRepository.getRepositoryByProject(projectId, repoType);
		if (CollectionUtils.isEmpty(repositoryNameList)) {
			return new ArrayList<>();
		}

		List<NexusRepositoryDTO> resultAll = new ArrayList<>();
		repositoryNameList.forEach(repositoryName -> {
			NexusServerRepository serverRepository = nexusServerRepositoryMap.get(repositoryName);
			if (NexusConstants.RepoType.MAVEN.equals(repoType)) {
				if (serverRepository != null && NexusApiConstants.VersionPolicy.RELEASE.equals(serverRepository.getVersionPolicy())
						&& NexusApiConstants.RepositoryType.HOSTED.equals(serverRepository.getType())) {
					// 包上传时，需要限制为RELEASE
					NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
					nexusRepositoryDTO.setName(repositoryName);
					resultAll.add(nexusRepositoryDTO);
				}
			} else if (NexusConstants.RepoType.NPM.equals(repoType)) {
				if (serverRepository != null && NexusApiConstants.RepositoryType.HOSTED.equals(serverRepository.getType())) {
					// 包上传时，需要限制为hosted
					NexusRepositoryDTO nexusRepositoryDTO = new NexusRepositoryDTO();
					nexusRepositoryDTO.setName(repositoryName);
					resultAll.add(nexusRepositoryDTO);
				}
			}

		});
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return resultAll;
	}

	@Override
	public NexusGuideDTO mavenRepoGuide(String repositoryName, Boolean showPushFlag) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);

		NexusRepository query = new NexusRepository();
		query.setNeRepositoryName(repositoryName);
		NexusRepository nexusRepository = nexusRepositoryRepository.selectOne(query);
		NexusUser nexusUser = null;
		if (nexusRepository != null) {
			NexusUser queryUser = new NexusUser();
			queryUser.setRepositoryId(nexusRepository.getRepositoryId());
			nexusUser = nexusUserRepository.selectOne(queryUser);
		}



		NexusServerRepository nexusServerRepository = nexusClient.getRepositoryApi().getRepositoryByName(repositoryName);
		if (nexusServerRepository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}

		// 返回信息
		NexusGuideDTO nexusGuideDTO = new NexusGuideDTO();
		// 设置拉取配置信息
		nexusGuideDTO.handlePullGuideValue(nexusServerRepository, nexusRepository, nexusUser);
		// 设置发布配置信息
		nexusGuideDTO.handlePushGuideValue(nexusServerRepository, nexusRepository, nexusUser, showPushFlag);
		// remove配置信息
		nexusClient.removeNexusServerInfo();
		return nexusGuideDTO;
	}

	@Override
	public Page<NexusRepositoryDTO> listNpmRepo(PageRequest pageRequest, NexusRepositoryQueryDTO queryDTO, String queryData) {
		// 设置并返回当前nexus服务信息
		configService.setNexusInfo(nexusClient);
		List<NexusRepositoryDTO> resultAll = this.queryRepo(queryDTO, queryData, NexusApiConstants.NexusRepoFormat.NPM_FORMAT);
		if (CollectionUtils.isEmpty(resultAll)) {
			return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
		}
		// remove配置信息
		nexusClient.removeNexusServerInfo();

		return PageConvertUtils.convert(pageRequest.getPage(), pageRequest.getSize(), resultAll);
	}

	private List<NexusRepositoryDTO> queryRepo(NexusRepositoryQueryDTO queryDTO, String queryData, String nexusRepoFormat) {
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(nexusRepoFormat);
		if (CollectionUtils.isEmpty(nexusServerRepositoryList)) {
			return new ArrayList<>();
		}

		List<NexusRepositoryDTO> resultAll = new ArrayList<>();
		switch (queryData) {
			case NexusConstants.RepoQueryData.REPO_ALL:
				this.mavenRepoAll(nexusServerRepositoryList, queryDTO, resultAll);
				break;
			case NexusConstants.RepoQueryData.REPO_EXCLUDE_PROJECT:
				this.mavenRepoExcludeProject(nexusServerRepositoryList, queryDTO, resultAll);
				break;
			case NexusConstants.RepoQueryData.REPO_ORG:
				this.mavenRepoOrg(nexusServerRepositoryList, queryDTO, resultAll);
				break;
			case NexusConstants.RepoQueryData.REPO_PROJECT:
				this.mavenRepoProject(nexusServerRepositoryList, queryDTO, resultAll);
				break;
			default:
				break;
		}

		// 项目名称查询
		Set<Long> projectIdSet = resultAll.stream().map(NexusRepositoryDTO::getProjectId).collect(Collectors.toSet());
		List<ProjectVO> projectVOList = baseServiceFeignClient.queryByIds(projectIdSet);
		Map<Long, ProjectVO> projectVOMap = projectVOList.stream().collect(Collectors.toMap(ProjectVO::getId, a -> a, (k1, k2) -> k1));
		resultAll.forEach(nexusRepositoryDTO -> {
			ProjectVO projectVO = projectVOMap.get(nexusRepositoryDTO.getProjectId());
			if (projectVO != null) {
				nexusRepositoryDTO.setProjectName(projectVO.getName());
				nexusRepositoryDTO.setProjectImgUrl(projectVO.getImageUrl());
			}
		});

		// 查询参数
		if (queryDTO.getRepositoryName() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getName().toLowerCase().contains(queryDTO.getRepositoryName().toLowerCase())).collect(Collectors.toList());
		}
		if (queryDTO.getType() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getType().toLowerCase().contains(queryDTO.getType().toLowerCase())).collect(Collectors.toList());
		}
		if (queryDTO.getVersionPolicy() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					nexusRepositoryDTO.getVersionPolicy() != null && nexusRepositoryDTO.getVersionPolicy().toLowerCase().contains(queryDTO.getVersionPolicy().toLowerCase())).collect(Collectors.toList());
		}
		if (queryDTO.getProjectId() != null) {
			resultAll = resultAll.stream().filter(nexusRepositoryDTO ->
					Objects.equals(queryDTO.getProjectId(), nexusRepositoryDTO.getProjectId())).collect(Collectors.toList());
		}

		return resultAll;
	}

	/**
	 * 制品库类型，转换为nexus format
	 * @return
	 */
	@Override
	public String convertRepoTypeToFormat(String repoType) {
		if (NexusConstants.RepoType.MAVEN.equals(repoType)) {
			return NexusApiConstants.NexusRepoFormat.MAVEN_FORMAT;
		} else if (NexusConstants.RepoType.NPM.equals(repoType)) {
			return NexusApiConstants.NexusRepoFormat.NPM_FORMAT;
		} else {
			return null;
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@Saga(code = NexusSagaConstants.NexusRepoEnableAndDisable.NEXUS_REPO_ENABLE_AND_DISABLE,
			description = "项目层-nexus仓库生效与失效",
			inputSchemaClass = NexusRepository.class)
	public void nexusRepoEnableAndDisAble(Long organizationId, Long projectId, Long repositoryId, String enableFlag) {
		NexusRepository repository = nexusRepositoryRepository.selectByPrimaryKey(repositoryId);
		if (repository == null) {
			throw new CommonException(BaseConstants.ErrorCode.DATA_NOT_EXISTS);
		}

		if (enableFlag.equals(NexusConstants.Flag.Y)) {
			if (repository.getEnableFlag().equals(enableFlag)) {
				throw new CommonException(NexusMessageConstants.NEXUS_REPO_IS_ENABLE);
			}

		} else if (enableFlag.equals(NexusConstants.Flag.N)) {
			if (repository.getEnableFlag().equals(enableFlag)) {
				throw new CommonException(NexusMessageConstants.NEXUS_REPO_IS_DISABLE);
			}
		} else {
			throw new CommonException(NexusMessageConstants.NEXUS_PARAM_ERROR);
		}

		repository.setEnableFlag(enableFlag);
		producer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(NexusSagaConstants.NexusRepoEnableAndDisable.NEXUS_REPO_ENABLE_AND_DISABLE)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("nexusRepoEnableAndDisAble")
						.withSourceId(projectId),
				startSagaBuilder -> {
					nexusRepositoryRepository.updateOptional(repository, NexusRepository.FIELD_ENABLE_FLAG);
					startSagaBuilder.withPayloadAndSerialize(repository).withSourceId(repository.getRepositoryId());
				});
	}

	@Override
	public List<NexusRepoDTO> getRepoByProject(Long organizationId, Long projectId, String repoType) {
		// TODO 自定义仓库后，添加条件
		NexusRepository query = new NexusRepository();
		query.setRepoType(repoType);
		query.setProjectId(projectId);
		List<NexusRepository> nexusRepositoryList = nexusRepositoryRepository.select(query);
		if (CollectionUtils.isEmpty(nexusRepositoryList)) {
			return new ArrayList<>();
		}
		List<NexusRepoDTO> result = new ArrayList<>();

		configService.setNexusInfo(nexusClient);
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(repoType));
		Map<String, NexusServerRepository> repoMap = nexusServerRepositoryList.stream().collect(Collectors.toMap(NexusServerRepository::getName, k -> k));
		nexusRepositoryList.forEach(nexusRepository -> {
			if (repoMap.containsKey(nexusRepository.getNeRepositoryName())) {
				NexusServerRepository nexusServerRepository = repoMap.get(nexusRepository.getNeRepositoryName());
				NexusRepoDTO nexusRepoDTO = new NexusRepoDTO();
				nexusRepoDTO.setRepositoryId(nexusRepository.getRepositoryId());
				nexusRepoDTO.setName(nexusRepository.getNeRepositoryName());
				nexusRepoDTO.setType(nexusServerRepository.getType());
				nexusRepoDTO.setUrl(nexusServerRepository.getUrl());
				nexusRepoDTO.setVersionPolicy(nexusServerRepository.getVersionPolicy());
				result.add(nexusRepoDTO);
			}
		});
		nexusClient.removeNexusServerInfo();
		return result;
	}

	@Override
	public List<NexusRepoDTO> getRepoUserByProject(Long organizationId, Long projectId, List<Long> repositoryIds) {
		List<NexusRepoDTO> result = new ArrayList<>();

		// TODO 自定义仓库后，添加条件
		if (CollectionUtils.isEmpty(repositoryIds)) {
			return result;
		}
		List<NexusRepoDTO> nexusRepositoryList = nexusRepositoryRepository.selectInfoByIds(repositoryIds);
		if (CollectionUtils.isEmpty(nexusRepositoryList)) {
			return result;
		}
		configService.setNexusInfo(nexusClient);
		List<NexusServerRepository> nexusServerRepositoryList = nexusClient.getRepositoryApi().getRepository(this.convertRepoTypeToFormat(null));
		Map<String, NexusServerRepository> repoMap = nexusServerRepositoryList.stream().collect(Collectors.toMap(NexusServerRepository::getName, k -> k));
		nexusRepositoryList.forEach(nexusRepoDTO -> {
			if (repoMap.containsKey(nexusRepoDTO.getName())) {
				NexusServerRepository nexusServerRepository = repoMap.get(nexusRepoDTO.getName());
				nexusRepoDTO.setType(nexusServerRepository.getType());
				nexusRepoDTO.setUrl(nexusServerRepository.getUrl());
				nexusRepoDTO.setVersionPolicy(nexusServerRepository.getVersionPolicy());
				if (nexusRepoDTO.getNeUserPassword() != null) {
					nexusRepoDTO.setNeUserPassword(DESEncryptUtil.decode(nexusRepoDTO.getNeUserPassword()));
				}
				if (nexusRepoDTO.getNePullUserPassword() != null) {
					nexusRepoDTO.setNePullUserPassword(DESEncryptUtil.decode(nexusRepoDTO.getNePullUserPassword()));
				}
				result.add(nexusRepoDTO);
			}
		});
		nexusClient.removeNexusServerInfo();
		return result;
	}
}
