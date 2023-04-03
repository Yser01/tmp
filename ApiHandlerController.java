package com.cestc.dc.apihandler.controller;

import com.cestc.dc.apihandler.service.*;
import com.cestc.dc.apihandler.signature.SignatureAnnotation;
import com.cestc.dc.apihandler.signature.SignatureParam;
import com.cestc.dc.common.apiLog.ApiLog;
import com.cestc.dc.common.commonBean.ResultVO;
import com.cestc.dc.common.enums.ResultCodeEnum;
import com.cestc.dc.common.exception.CommonException;
import com.cestc.dc.repository.domain.entity.api.*;
import com.cestc.dc.repository.domain.entity.department.QueryMembersParam;
import com.cestc.dc.repository.domain.entity.user.DepartTreeNode;
import com.cestc.dc.repository.domain.request.*;
import com.cestc.dc.repository.domain.vo.UserAllowPermissionVo;
import com.cestc.dc.repository.domain.vo.UserPermissionVo;
import com.cestc.dc.sso.service.SsoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping(path = "apihandler")
@Api(tags = "api管理")
public class ApiHandlerController {

    /**
     * 权限相关逻辑类
     */
    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserInfosService userInfosService;

    @Autowired
    private DpmtGroupService dpmtGroupService;

    @Autowired
    private SubSystemService systemService;

    @Autowired
    private ApiStatisticsService apiStatisticsService;

    @Autowired
    private LdapUserSynService ldapUserSynService;

    @Autowired
    private SsoService ssoService;

    @Autowired
    private DepartService departService;

    /**
     * 校验用户是否存在该权限
     *
     * @param
     * @return
     */
    @PostMapping("/permissionCheck")
    @ApiOperation(value = "校验用户是否存在该权限")
    @SignatureAnnotation()
    @ApiLog(type = 2)
    public ResultVO<UserAllowPermissionVo> checkUserPermission(@RequestBody @Validated final SignatureParam<PermissionAllowReq> signatureParam) {
        try {
            PermissionAllowReq permissionAllowReq = signatureParam.getData();
            return new ResultVO<>(permissionService.checkUserPermission(permissionAllowReq));
        } catch (CommonException e) {
            return ResultVO.failed(e.getMessage());
        } catch (Exception e) {
            log.error("校验用户是否存在该权限失败，错误信息:", e);
            return ResultVO.failed("内部错误");
        }
    }


    /**
     * 根据用户名获取用户对应权限编码
     *
     * @param
     * @return
     */
    @PostMapping("/getUserPermission")
    @ApiOperation(value = "获取用户对应权限")
    @SignatureAnnotation(checkBind = false)
    public ResultVO<UserPermissionVo> getUserPermission(@RequestBody @Validated final SignatureParam<PermissionReq> signatureParam) {
        try {
            PermissionReq permissionReq = signatureParam.getData();
            UserPermissionVo resultData = permissionService.getUserPermission(permissionReq);
            Set<String> permissionSet = resultData.getPolicyCodes();
            if (!CollectionUtils.isEmpty(permissionSet)) {
                return new ResultVO<>(resultData);
            } else {
                return new ResultVO<>(ResultCodeEnum.APP_PERMISSION_DENY);
            }
        } catch (CommonException e) {
            return ResultVO.failed(e.getMessage());
        } catch (Exception e) {
            log.error("获取用户对应权限失败，错误信息:", e);
            return ResultVO.failed("内部错误");
        }

    }

    /**
     * 获取单个用户信息接口
     *
     * @return
     * @author wumingkai
     */
    @SignatureAnnotation
    @PostMapping("/getOneUser")
    @ApiOperation(value = "获取单个用户信息接口")
    @ApiLog(type = 2)
    public ResultVO getOneUser(@RequestBody SignatureParam<UserInfosReq> signatureParam) {
        UserInfosReq userInfosReq = signatureParam.getData();
        String uid = userInfosReq.getUid();
        return userInfosService.getUser(uid, userInfosReq.getStatus());
    }

    /**
     * 根据用户名，电话，邮箱集合获取用户信息集合
     */
    @SignatureAnnotation
    @PostMapping("/getUsersByInfos")
    @ApiOperation(value = "根据用户名，电话，邮箱集合获取用户信息集合")
    @ApiLog(type = 2)
    public ResultVO getUsersByInfos(@RequestBody SignatureParam<ApiUserInfoRequest> userInfoRequest) {
        return userInfosService.getUsersByInfos(userInfoRequest);
    }


    /**
     * 根据用户名集合获取用户信息集合
     */
    @SignatureAnnotation
    @PostMapping("/getUsersByUids")
    @ApiOperation(value = "根据用户名集合获取用户信息集合")
    @ApiLog(type = 2)
    public ResultVO getUsersByUids(@RequestBody SignatureParam<List<String>> signatureParam) {
        return userInfosService.getUsersByUids(signatureParam);
    }


    /**
     * 部门组成员列表获取接口
     *
     * @return
     * @author wumingkai
     */
    @SignatureAnnotation
    @PostMapping("/getDepGroups")
    @ApiOperation(value = "部门组成员列表获取接口")
    @ApiLog(type = 2)
    public ResultVO getDepGroups(@RequestBody SignatureParam<QueryMembersParam> signatureParam) {
        QueryMembersParam param = signatureParam.getData();
        return dpmtGroupService.queryMembers(param);
    }


    @SignatureAnnotation
    @PostMapping("/getLoginData")
    @ApiOperation(value = "获取登录数据")
    @ApiLog(type = 2)
    public ResultVO<ApiPageVo<LoginApi>> getLoginData(@RequestBody SignatureParam<LoginDataRequest> signatureParam) {
        LoginDataRequest param = signatureParam.getData();
        return apiStatisticsService.getLoginData(param);
    }

    @SignatureAnnotation
    @PostMapping("/getOrgList")
    @ApiOperation(value = "获取组织架构集合")
    @ApiLog(type = 2)
    public ResultVO<List<OrgResponse>> getOrgList(@RequestBody SignatureParam<OrgListRequest> signatureParam) {
        OrgListRequest param = signatureParam.getData();
        return ldapUserSynService.getOrgList(param);
    }

    @SignatureAnnotation
    @PostMapping("/getStaffList")
    @ApiOperation(value = "获取人员集合")
    @ApiLog(type = 2)
    public ResultVO getStaffList(@RequestBody SignatureParam<OrgListRequest> signatureParam) {
        OrgListRequest param = signatureParam.getData();
        return ldapUserSynService.getStaffList(param);
    }

    @SignatureAnnotation
    @PostMapping("/changeUserInfo")
    @ApiOperation(value = "修改用户基本信息")
    @ApiLog(type = 2)
    public ResultVO changeUserInfo(@RequestBody SignatureParam<UserInfoChangeReq> signatureParam) {
        UserInfoChangeReq param = signatureParam.getData();
        return userInfosService.changeUserInfo(param);
    }

    @SignatureAnnotation
    @PostMapping("/updatePassword")
    @ApiOperation(value = "修改用户密码")
    @ApiLog(type = 2)
    public ResultVO updatePassword(@RequestBody SignatureParam<UserPasswordRequest> signatureParam) {
        UserPasswordRequest param = signatureParam.getData();
        return userInfosService.updatePassword(param);
    }

    /**
     * 签名生成测试接口
     *
     * @return
     */
    @PostMapping("/getSignatureTest")
    @ApiOperation(value = "签名生成测试接口")
    public ResultVO getSignatureTest(@RequestParam String productCode) {
        return systemService.getSignatureTest(productCode);
    }

    @GetMapping(value = "/getPublicApis")
    @ApiOperation(value = "获取公共接口列表")
    public ResultVO getPublicApis() {
        return systemService.getPublicApis();
    }


    @SignatureAnnotation
    @PostMapping("/getUsersByEmp")
    @ApiOperation(value = "根据工号获取用户信息集合")
    @ApiLog(type = 2)
    public ResultVO getUsersByEmp(@RequestBody SignatureParam<ApiUserEmpRequest> signatureParam) {
        return userInfosService.getUsersByEmp(signatureParam.getData());
    }

    @SignatureAnnotation
    @PostMapping("/getWorkPlacesByOrder")
    @ApiOperation(value = "获取根据人数排序之后的办公地点")
    @ApiLog(type = 2)
    public ResultVO getWorkPlacesByOrder(@RequestBody SignatureParam<ApiWorkPlacesRequest> signatureParam) {
        return userInfosService.getWorkPlacesByOrder(signatureParam.getData());
    }

    @SignatureAnnotation
    @PostMapping("/getStaffListByDept")
    @ApiOperation(value = "获取部门编码下所有的人员")
    @ApiLog(type = 2)
    public ResultVO getStaffListByDept(@RequestBody SignatureParam<StaffListByDeptRequest> signatureParam) {
        return userInfosService.getStaffListByDept(signatureParam.getData());
    }

    @SignatureAnnotation
    @PostMapping("/getUserAndName")
    @ApiOperation(value = "获取在职人员的账号和姓名")
    @ApiLog(type = 2)
    public ResultVO getUserAndName(@RequestBody SignatureParam<OrgListRequest> signatureParam) {
        return userInfosService.getUserAndName(signatureParam.getData());
    }

    @SignatureAnnotation
    @PostMapping("/batchTokenCheck")
    @ApiOperation(value = "批量校验token")
    @ApiLog(type = 2)
    public ResultVO batchTokenCheck(@RequestBody SignatureParam<BatchTokenCheckRequest> signatureParam) {
        return ssoService.batchTokenCheck(signatureParam.getData());
    }

    @SignatureAnnotation
    @PostMapping("/fuzzySearchTree")
    @ApiOperation(value = "模糊查询部门或者人员")
    @ApiLog(type = 2)
    public ResultVO<DepartTreeNode> fuzzySearchTree(@RequestBody SignatureParam<String> signatureParam) {
        String keyword = signatureParam.getData();
        DepartTreeNode node = departService.fuzzySearchTree(keyword);
        return ResultVO.success(node);
    }
}
