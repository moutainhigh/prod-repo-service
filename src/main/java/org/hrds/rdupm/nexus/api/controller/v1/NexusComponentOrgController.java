package org.hrds.rdupm.nexus.api.controller.v1;

import com.github.pagehelper.PageInfo;
import io.choerodon.core.annotation.Permission;
import io.choerodon.core.enums.ResourceType;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.hrds.rdupm.nexus.api.dto.NexusComponentGuideDTO;
import org.hrds.rdupm.nexus.app.service.NexusComponentService;
import org.hrds.rdupm.nexus.client.nexus.model.NexusComponentQuery;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerAssetUpload;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerComponentInfo;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerComponentUpload;
import org.hrds.rdupm.nexus.infra.constant.NexusMessageConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.util.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * 组织层 制品库_nexus 包信息 管理 API
 * @author weisen.yang@hand-china.com 2020/4/2
 */
@RestController("nexusComponentOrgController.v1")
@RequestMapping("/v1/nexus-components/organizations")
public class NexusComponentOrgController extends BaseController {
	@Autowired
	private NexusComponentService nexusComponentService;

	@ApiOperation(value = "组织层-包列表查询")
	@Permission(type = ResourceType.ORGANIZATION, permissionPublic = true)
	@GetMapping("/{organizationId}")
	public ResponseEntity<PageInfo<NexusServerComponentInfo>> listComponents(@ApiParam(value = "组织ID", required = true) @PathVariable(name = "organizationId") Long organizationId,
																			 NexusComponentQuery componentQuery,
																			 @ApiIgnore PageRequest pageRequest) {
		return Results.success(nexusComponentService.listComponents(organizationId, null, false,componentQuery, pageRequest));
	}

}
