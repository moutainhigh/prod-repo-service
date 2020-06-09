package org.hrds.rdupm.harbor.domain.entity;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotBlank;
import java.util.Date;
import java.util.Set;

import io.choerodon.mybatis.domain.AuditDomain;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 制品库-harbor自定义镜像仓库表
 *
 * @author mofei.li@hand-china.com 2020-06-02 09:51:58
 */
@Getter
@Setter
@ApiModel("制品库-harbor自定义镜像仓库表")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "rdupm_harbor_custom_repo")
public class HarborCustomRepo extends AuditDomain {

    public static final String FIELD_ID = "id";

    public static final String FIELD_PROJECT_ID = "projectId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";

    public static final String FIELD_NAME = "repo_name";
    public static final String FIELD_URL = "repo_url";
    public static final String FIELD_LOGIN_NAME = "loginName";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_PUBLIC_FLAG = "publicFlag";
    public static final String FIELD_CREATION_DATE = "creationDate";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";
    public static final String FIELD_LAST_UPDATE_DATE = "lastUpdateDate";
    public static final String FIELD_LAST_UPDATE_LOGIN = "lastUpdateLogin";

    //
    // 业务方法(按public protected private顺序排列)
    // ------------------------------------------------------------------------------

    //
    // 数据库字段
    // ------------------------------------------------------------------------------


    @ApiModelProperty("表ID，主键，供其他表做外键")
    @Id
    @GeneratedValue
    private Long id;

    @ApiModelProperty(value = "猪齿鱼项目ID")
    private Long projectId;

    @ApiModelProperty(value = "猪齿鱼组织ID")
    private Long organizationId;

    @ApiModelProperty(value = "自定义镜像仓库名称（harbor项目名）",required = true)
    @NotBlank
    private String repoName;
    @ApiModelProperty(value = "自定义镜像仓库地址",required = true)
    @NotBlank
    private String repoUrl;
    @ApiModelProperty(value = "登录名",required = true)
    @NotBlank
    private String loginName;
    @ApiModelProperty(value = "密码",required = true)
    @NotBlank
    private String password;
    @ApiModelProperty(value = "邮箱",required = true)
    @NotBlank
    private String email;
   @ApiModelProperty(value = "描述")
    private String description;
    @ApiModelProperty(value = "是否公开访问，默认false",required = true)
    @NotBlank
    private String publicFlag;
	//
    // 非数据库字段
    // ------------------------------------------------------------------------------

	@Transient
	@ApiModelProperty(value = "关联的应用服务ID")
	private Set<Long> appServiceIds;


    //
    // getter/setter
    // ------------------------------------------------------------------------------

}