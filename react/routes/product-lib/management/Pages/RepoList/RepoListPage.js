/**
* 制品库项目层项目列表
* @author JZH <zhihao.jiang@hand-china.com>
* @creationDate 2020/4/24
* @copyright 2020 ® HAND
*/
import React, { useMemo, useEffect, useState } from 'react';
// import { Content } from '@choerodon/boot';
import { Modal, Spin, Stores, Button } from 'choerodon-ui/pro';
import { Icon, message, Tooltip } from 'choerodon-ui';
import { observer, useLocalStore, Observer } from 'mobx-react-lite';
import { axios, stores, Action } from '@choerodon/boot';
import { reaction } from 'mobx';
import { intlPrefix } from '../../index';
import { CurrentRoleContext } from '../index';
import { MavenEditModal, DockerEditModal, NpmEditModal, DockerCustomEditModal } from '../EditModal';
import { useStore } from '../../index';
import './index.less';

const stopKey = Modal.key();

const RepoList = ({ setActiveRepository }) => {
  const dockerCustomModal = React.useRef();
  const {
    intl: { formatMessage },
    dockerCustomCreateDs, repoListDs, npmCreateDs, mavenCreateDs, dockerCreateBasicDs,
  } = useStore();

  const [VERSION_POLICY, setVersionPolicy] = useState([]);
  const [REPOSITORY_TYPE, setRepositoryType] = useState([]);
  const currentRole = React.useContext(CurrentRoleContext);

  useEffect(() => {
    async function init() {
      const lookupData2 = await Stores.LookupCodeStore.fetchLookupData('/hpfm/v1/lovs/value?lovCode=RDUPM.MAVEN_VERSION_POLICY');
      setVersionPolicy(lookupData2);
      const type = await Stores.LookupCodeStore.fetchLookupData('/hpfm/v1/lovs/value?lovCode=RDUPM.MAVEN_REPOSITORY_TYPE');
      setRepositoryType(type);
    }
    init();
  }, []);

  async function handleChangeStatus(item, params) {
    const { repositoryId } = item;
    const { currentMenuType: { projectId, organizationId } } = stores.AppState;
    const enableFlag = params === 'disable' ? 'N' : 'Y';
    await axios.post(`/rdupm/v1/nexus-repositorys/${organizationId}/project/${projectId}/enable?repositoryId=${repositoryId}&enableFlag=${enableFlag}`)
      .then((res) => {
        if (res.failed) {
          message.error(res.message);
          return false;
        } else {
          message.success(formatMessage({ id: 'success.operation' }));
          repoListDs.query();
          return true;
        }
      });
  }
  function openChangeActive(item, params) {
    Modal.open({
      movable: false,
      closable: false,
      key: stopKey,
      title: formatMessage({ id: `${intlPrefix}.action.${params}.title` }, { name: item.name }),
      children: formatMessage({ id: `${intlPrefix}.action.${params}.tips` }),
      onOk: () => handleChangeStatus(item, params),
    });
  }

  const handleDeleteDocker = () => {
    const deleteKey = Modal.key();
    Modal.open({
      key: deleteKey,
      title: formatMessage({ id: 'confirm.delete' }),
      children: formatMessage({ id: `${intlPrefix}.view.confirm.deleteMirror` }),
      okText: formatMessage({ id: 'delete' }),
      okProps: { color: 'red' },
      cancelProps: { color: 'dark' },
      onOk: async () => {
        const { currentMenuType: { projectId } } = stores.AppState;
        try {
          await axios.delete(`/rdupm/v1/harbor-project/delete/${projectId}`);
          message.success(formatMessage({ id: 'success.delete', defaultMessage: '删除成功' }));
          repoListDs.query();
        } catch (error) {
          // message.error(error);
        }
      },
      footer: ((okBtn, cancelBtn) => (
        <React.Fragment>
          {cancelBtn}{okBtn}
        </React.Fragment>
      )),
      movable: false,
    });
  };

  const handleDeleteCustomDocker = (record) => {
    const deleteKey = Modal.key();
    Modal.open({
      key: deleteKey,
      title: formatMessage({ id: 'confirm.delete' }),
      children: formatMessage({ id: `${intlPrefix}.view.confirm.deleteMirror` }),
      okText: formatMessage({ id: 'delete' }),
      okProps: { color: 'red' },
      cancelProps: { color: 'dark' },
      onOk: async () => {
        const { currentMenuType: { organizationId, projectId } } = stores.AppState;
        try {
          await axios.delete(`/rdupm/v1/${organizationId}/harbor-custom-repos/delete/${projectId}`, { data: record });
          message.success(formatMessage({ id: 'success.delete', defaultMessage: '删除成功' }));
          repoListDs.query();
        } catch (error) {
          // message.error(error);
        }
      },
      footer: ((okBtn, cancelBtn) => (
        <React.Fragment>
          {cancelBtn}{okBtn}
        </React.Fragment>
      )),
      movable: false,
    });
  };

  const handleDelete = (record) => {
    if (record.productType === 'DOCKER') {
      handleDeleteDocker();
    }
    if (record.productType === 'DOCKER_CUSTOM') {
      handleDeleteCustomDocker(record);
    }
  };

  const mavenEditModalProps = useMemo(() => ({ formatMessage, mavenCreateDs, repoListDs }), [repoListDs, mavenCreateDs, formatMessage]);
  const npmEditModalProps = useMemo(() => ({ formatMessage, npmCreateDs, repoListDs }), [repoListDs, npmCreateDs, formatMessage]);
  const dockerEditModalProps = useMemo(() => ({ formatMessage, dockerCreateBasicDs, repoListDs }), [repoListDs, dockerCreateBasicDs, formatMessage]);
  const dockerCustomEditModalProps = useMemo(() => ({ formatMessage, dockerCustomCreateDs, repoListDs }), [repoListDs, dockerCustomCreateDs, formatMessage]);

  const handleEditMaven = async (data) => {
    const { currentMenuType: { projectId, organizationId } } = stores.AppState;
  
    const key = Modal.key();

    const [res, nuxesList] = await Promise.all([
      axios.get(`/rdupm/v1/nexus-repositorys/${organizationId}/project/${projectId}/maven/repo/${data.repositoryId}`),
      axios.get(`/rdupm/v1/${organizationId}/nexus-server-configs/project/${projectId}/list`),
    ]);
    
    const enableFlagItem = nuxesList.find(o => o.enableFlag === 1);
    const { enableAnonymousFlag } = enableFlagItem;

    Modal.open({
      key,
      title: formatMessage({ id: `${intlPrefix}.view.changeRepo`, defaultMessage: '修改仓库' }),
      maskClosable: false,
      destroyOnClose: true,
      drawer: true,
      className: 'product-lib-edit-model',
      children: <MavenEditModal {...mavenEditModalProps} originData={res} enableAnonymousFlag={enableAnonymousFlag} />,
    });
  };

  const handleEditNpm = async (data) => {
    const { currentMenuType: { projectId, organizationId } } = stores.AppState;
    
    const key = Modal.key();

    const [res, nuxesList] = await Promise.all([
      axios.get(`/rdupm/v1/nexus-repositorys/${organizationId}/project/${projectId}/npm/repo/${data.repositoryId}`),
      axios.get(`/rdupm/v1/${organizationId}/nexus-server-configs/project/${projectId}/list`),
    ]);
    
    
    const enableFlagItem = nuxesList.find(o => o.enableFlag === 1);
    const { enableAnonymousFlag } = enableFlagItem;

    Modal.open({
      key,
      title: formatMessage({ id: `${intlPrefix}.view.changeRepo`, defaultMessage: '修改仓库' }),
      maskClosable: false,
      destroyOnClose: true,
      drawer: true,
      className: 'product-lib-edit-model',
      children: <NpmEditModal {...npmEditModalProps} originData={res} enableAnonymousFlag={enableAnonymousFlag} />,
    });
  };


  const handleEditDocker = (data) => {
    const key = Modal.key();
    Modal.open({
      key,
      title: formatMessage({ id: `${intlPrefix}.view.changeRepo`, defaultMessage: '修改制品库' }),
      maskClosable: false,
      destroyOnClose: true,
      drawer: true,
      className: 'product-lib-edit-model',
      children: <DockerEditModal {...dockerEditModalProps} harborId={data.harborId} />,
    });
  };

  const validateStore = useLocalStore(() => ({
    isValidate: undefined,
    setIsValidate(value) {
      validateStore.isValidate = value;
    },
  }));

  const disabledListener = (disabled) => {
    dockerCustomModal.current.update({ okProps: { disabled } });
  };

  React.useEffect(
    () => reaction(() => !validateStore.isValidate, disabledListener),
    [],
  );

  const validateConnect = async () => {
    const { currentMenuType: { organizationId } } = stores.AppState;
    const validate = await dockerCustomCreateDs.current.validate();
    if (validate) {
      try {
        const res = await axios.post(`/rdupm/v1/${organizationId}/harbor-custom-repos/check/custom-repo`, dockerCustomCreateDs.current.toData());
        validateStore.setIsValidate(res);
      } catch (e) {
        validateStore.setIsValidate(false);  
      }
    }
  };

  const handleEditCustomDocker = (data) => {
    const key = Modal.key();
    dockerCustomModal.current = Modal.open({
      key,
      title: formatMessage({ id: `${intlPrefix}.view.changeRepo`, defaultMessage: '修改制品库' }),
      maskClosable: false,
      destroyOnClose: true,
      drawer: true,
      className: 'product-lib-edit-model',
      okProps: { disabled: true },
      children: <DockerCustomEditModal {...dockerCustomEditModalProps} validateStore={validateStore} repositoryId={data.repoId} />,
      footer: (okBtn, cancelBtn) => (
        <Observer>
          {() => (
            <React.Fragment>
              <Button color="primary" funcType="raised" onClick={validateConnect}>测试连接</Button>
              {[okBtn, cancelBtn]}
            </React.Fragment>
          )}
        </Observer>
      ),
    });
  };

  const handleEdit = (data) => {
    if (data.productType === 'MAVEN') {
      handleEditMaven(data);
    }
    if (data.productType === 'DOCKER') {
      handleEditDocker(data);
    }
    if (data.productType === 'NPM') {
      handleEditNpm(data);
    }
    if (data.productType === 'DOCKER_CUSTOM') {
      handleEditCustomDocker(data);
    }
  };

  const hasAuth = (productType, repositoryId) => {
    if (productType === 'DOCKER') {
      return currentRole.DOCKER[repositoryId].includes('projectAdmin');
    } else if (productType === 'MAVEN') {
      return currentRole.MAVEN[repositoryId].includes('projectAdmin');
    } else if (productType === 'NPM') {
      return currentRole.NPM[repositoryId].includes('projectAdmin');
    } else {
      return true;
    }
  };

  const repoLostData = repoListDs.toData();

  return (
    <ul className="product-lib-repolist">
      <Spin dataSet={repoListDs}>
        {repoLostData.map((data) => {
          const {
            uniqueId,
            name,
            creationDate,
            productType,
            publicFlag,
            creatorRealName,
            repoCount,
            creatorLoginName,
            versionPolicy,
            type,
            sourceRepositoryId,
            projectId,
            repoName,
          } = data;
          return (
            <li key={uniqueId} className="product-lib-repolist-card">
              <div style={{ display: 'flex', width: '100%', alignItems: 'center', height: '100%' }}>
                {['DOCKER', 'DOCKER_CUSTOM'].includes(productType) && <div className="product-lib-repolist-card-docker-img" />}
                {productType === 'MAVEN' && <div className="product-lib-repolist-card-maven-img" />}
                {productType === 'NPM' && <div className="product-lib-repolist-card-npm-img" />}
                <div className="product-lib-repolist-card-record-content">
                  <div className="product-lib-repolist-card-record-content-top">
                    <span className="product-lib-repolist-card-record-content-top-reponame" onClick={() => setActiveRepository(data)}>
                      <span className="link-cell">{name || repoName}</span>
                      {publicFlag === 'true' && <Icon type="unlock" style={{ color: 'rgba(104,135,232,1)', marginLeft: '6px', fontSize: 14 }} />}
                      {publicFlag === 'false' && <Icon type="lock" style={{ color: 'rgba(104,135,232,1)', marginLeft: '6px', fontSize: 14 }} />}
                      {productType === 'DOCKER' && <span className="product-lib-repolist-card-record-content-top-reponame-custom-harbor">默认仓库</span>}
                      {productType === 'DOCKER_CUSTOM' && <span className="product-lib-repolist-card-record-content-top-reponame-custom-harbor">自定义仓库</span>}
                    </span>

                  </div>

                  <div className="product-lib-repolist-card-record-content-bottom">
                    <div className="product-lib-repolist-card-record-content-bottom-field">
                      <div className="product-lib-repolist-card-record-content-bottom-field-label">
                        <Icon type="account_circle-o" />
                        {`${formatMessage({ id: 'createdByName', defaultMessage: '创建人' })}：`}
                      </div>
                      <div className="product-lib-repolist-card-record-content-bottom-field-value">
                        <Tooltip title={`${creatorRealName}(${creatorLoginName})`}>
                          {`${creatorRealName}(${creatorLoginName})`}
                        </Tooltip>
                      </div>
                    </div>

                    <div className="product-lib-repolist-card-record-content-bottom-field">
                      <div className="product-lib-repolist-card-record-content-bottom-field-label">
                        <Icon type="date_range-o" />
                        {`${formatMessage({ id: 'creationDate', defaultMessage: '创建时间' })}：`}
                      </div>
                      <div className="product-lib-repolist-card-record-content-bottom-field-value">
                        {creationDate}
                      </div>
                    </div>
                    {productType === 'DOCKER' &&
                      <div className="product-lib-repolist-card-record-content-bottom-field">
                        <div className="product-lib-repolist-card-record-content-bottom-field-label">
                          <Icon type="dns-o" />
                          {`${formatMessage({ id: 'infra.prod.lib.model.repoCount', defaultMessage: '镜像数' })}：`}
                        </div>
                        <div className="product-lib-repolist-card-record-content-bottom-field-value">
                          {repoCount}
                        </div>
                      </div>
                    }

                    {['MAVEN', 'NPM'].includes(productType) &&
                      <React.Fragment>
                        {type &&
                          <div className="product-lib-repolist-card-record-content-bottom-field" style={{ width: '18%' }}>
                            <div className="product-lib-repolist-card-record-content-bottom-field-label">
                              <Icon type="category-o" />
                              {`${formatMessage({ id: 'infra.prod.lib.model.type', defaultMessage: '仓库类型' })}：`}
                            </div>
                            <div className="product-lib-repolist-card-record-content-bottom-field-value">
                              {(REPOSITORY_TYPE.find(o => o.value === type) || {}).meaning}
                            </div>
                          </div>
                        }
                        {versionPolicy &&
                          <div className="product-lib-repolist-card-record-content-bottom-field" style={{ width: '18%' }}>
                            <div className="product-lib-repolist-card-record-content-bottom-field-label">
                              <Icon type="list" />
                              {`${formatMessage({ id: 'infra.prod.lib.model.versionPolicy', defaultMessage: '仓库策略' })}：`}
                            </div>
                            <div className="product-lib-repolist-card-record-content-bottom-field-value">
                              {(VERSION_POLICY.find(o => o.value === versionPolicy) || {}).meaning}
                            </div>
                          </div>
                        }
                      </React.Fragment>
                    }
                  </div>
                </div>

                <div style={{ marginRight: '20px' }}>
                  {(() => {
                    const actionData = [{
                      service: [
                        'choerodon.code.project.infra.product-lib.ps.project-owner-maven',
                        'choerodon.code.project.infra.product-lib.ps.project-member-maven',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-harbor',
                        'choerodon.code.project.infra.product-lib.ps.project-member-harbor',
                        'choerodon.code.project.infra.product-lib.ps.project-member-npm',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-npm',
                      ],
                      text: formatMessage({ id: 'infra.prod.lib.view.detail', defaultMessage: '查看详情' }),
                      action: () => setActiveRepository(data),
                    }];

                    const editMenu = {
                      service: [
                        'choerodon.code.project.infra.product-lib.ps.project-owner-maven',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-harbor',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-npm',
                      ],
                      text: formatMessage({ id: 'infra.prod.lib.view.changeConf', defaultMessage: '修改配置' }),
                      action: () => handleEdit(data),
                    };

                    const deleteMenu = {
                      service: [
                        'choerodon.code.project.infra.product-lib.ps.project-owner-maven',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-harbor',
                        'choerodon.code.project.infra.product-lib.ps.project-owner-npm',
                      ],
                      text: formatMessage({ id: 'delete', defaultMessage: '删除' }),
                      action: () => handleDelete(data),
                    };

                    if (['MAVEN', 'NPM'].includes(productType)) {
                      // maven、npm 失效/启用
                      const params = data.enableFlag === 'Y' ? 'disable' : 'active';
                      const disableAndAbleMenu = {
                        service: [
                          'choerodon.code.project.infra.product-lib.ps.project-owner-maven',
                          'choerodon.code.project.infra.product-lib.ps.project-owner-harbor',
                          'choerodon.code.project.infra.product-lib.ps.project-owner-npm',
                        ],
                        text: formatMessage({ id: `${intlPrefix}.action.${params}` }),
                        action: () => openChangeActive(data, params),
                      };


                      if (hasAuth(productType, sourceRepositoryId || projectId) && data.enableFlag === 'Y') {
                        actionData.unshift(editMenu);
                      }

                      if (hasAuth(productType, sourceRepositoryId || projectId)) {
                        actionData.unshift(disableAndAbleMenu);
                      }
                    }
                    if (['DOCKER', 'DOCKER_CUSTOM'].includes(productType)) {
                      if (hasAuth(productType, sourceRepositoryId || projectId)) {
                        actionData.unshift(
                          editMenu,
                          deleteMenu,
                        );
                      }
                    }
                    return <Action data={actionData} />;
                  })()
                  }
                </div>
              </div>
            </li>
          );
        })}
      </Spin>
    </ul >
  );
};

export default observer(RepoList);
