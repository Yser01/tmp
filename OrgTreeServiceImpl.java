package com.cestc.dc.apihandler.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.cestc.dc.apihandler.deptTree.FuzzySearchTree;
import com.cestc.dc.apihandler.service.DepartService;
import com.cestc.dc.apihandler.service.OrgTreeService;
import com.cestc.dc.common.domain.ConstantCommon;
import com.cestc.dc.common.enums.StatusEnum;
import com.cestc.dc.repository.common.RedisCommon;
import com.cestc.dc.repository.dao.AsDepartMapper;
import com.cestc.dc.repository.dao.UserPwdDao;
import com.cestc.dc.repository.domain.entity.orgTree.DepartTreeNode;
import com.cestc.dc.repository.domain.entity.user.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;
import javax.annotation.Resource;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.cestc.dc.apihandler.deptTree.FuzzySearchTree.convertToPinyin;
import static com.cestc.dc.common.domain.ConstantCommon.DEPART_MAP;
import static com.cestc.dc.common.domain.ConstantCommon.ROOT_DEPARTMENT_NUM;
/**
 * @author Yibowen
 * @date 2023-04-04
 */
@Service
@Slf4j
public class OrgTreeServiceImpl implements OrgTreeService {

    @Resource private AsDepartMapper departMapper;

    @Resource private RedisCommon redisCommon;

    @Resource private UserPwdDao userPwdDao;

    @Resource private DepartService departService;

    private final long expireTime = 60 * 60;
    private static FuzzySearchTree uidTree;

    private static FuzzySearchTree nameTree;

    private static FuzzySearchTree deptNameTree;

    private static DepartTreeNode fullTree;
    /**
     * 模糊查询部门或者人员 如果keyword为空，返回全量
     *
     * @param keyword 查询的关键字
     * @return 部门树根节点
     */
    @Override
    public DepartTreeNode fuzzySearchTree(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return fullTree;
        }

        Map<String, AsDepart> deptMap = getDeptMapFromCache();
        Map<String, DepartTreeNode> deptTreeNodeMap = new HashMap<>();

        List<DeptPersonVO> targetDepartments = getTargetDepartments(keyword, deptMap);
        buildTreeWithTargetDept(deptMap, targetDepartments, deptTreeNodeMap);

        List<UserVo> targetUsers = getTargetUsers(keyword);
        buildTreeWithTargetUser(deptMap, deptTreeNodeMap, targetUsers);

        //        log.info("deptTreeNodeMap:" + JSON.toJSONString(deptTreeNodeMap));
        return deptTreeNodeMap.getOrDefault(ROOT_DEPARTMENT_NUM, new DepartTreeNode());
    }

    private void buildTreeWithTargetUser(
            Map<String, AsDepart> deptMap,
            Map<String, DepartTreeNode> deptTreeNodeMap,
            List<UserVo> targetUsers) {
        targetUsers.forEach(insertUserIntoDeptTree(deptMap, deptTreeNodeMap));
    }

    private void buildTreeWithTargetDept(
            Map<String, AsDepart> deptMap,
            List<DeptPersonVO> targetDepartments,
            Map<String, DepartTreeNode> deptTreeNodeMap) {
        if (CollectionUtils.isNotEmpty(targetDepartments)) {
            targetDepartments.forEach(
                    deptPersonVO ->
                            constructPath(deptMap, deptTreeNodeMap, deptPersonVO.getBmdm()));
            putUserInDepts(targetDepartments, deptTreeNodeMap);
        }
    }

    private void putUserInDepts(
            List<DeptPersonVO> targetDepartments, Map<String, DepartTreeNode> deptTreeNodeMap) {
        List<String> targetDeptNames =
                targetDepartments.stream().map(DeptPersonVO::getBmdm).collect(Collectors.toList());
        Example example = new Example(AsUser.class);
        example.createCriteria()
                .andEqualTo("status", String.valueOf(ConstantCommon.OPERATION_RESULT_FAILED))
                .andIn("departmentNumber", targetDeptNames);
        List<UserPwd> usersFromTargetDept = userPwdDao.selectByExample(example);
        usersFromTargetDept.parallelStream()
                .forEach(
                        user -> {
                            String deptCode = user.getDepartmentNumber();
                            DepartTreeNode node = deptTreeNodeMap.get(deptCode);
                            if (node == null) {
                                log.error("部门树节点缓存中找不到,deptCode:{},user:{}", deptCode, user);
                                return;
                            }
                            UserVo userVo = UserVo.from(user);
                            node.addUser(userVo);
                        });
    }

    private List<UserVo> getTargetUsers(String keyword) {
        Example userEx = new Example(UserPwd.class);
        Example.Criteria criteria = userEx.createCriteria();
        criteria.andEqualTo("status", String.valueOf(ConstantCommon.OPERATION_RESULT_FAILED));
        if (StringUtils.isNotBlank(keyword)) {
            // 如果keyword不为空，就模糊搜索
            if (uidTree == null || nameTree == null) {
                generateUserFuzzySearchTrees();
            }
            List<String> targetUid = uidTree.search(keyword);
            List<String> targetName = nameTree.search(keyword);
            if (targetUid.isEmpty() && targetName.isEmpty()) {
                return Collections.emptyList();
            }

            Example.Criteria and = userEx.and();
            if (!targetUid.isEmpty()) {
                // 如果为空，tkmybatis会产生错误的sql语句
                and.orIn("uid", targetUid);
            }
            if (!targetName.isEmpty()) {
                and.orIn("displayName", targetName);
            }
        }
        List<UserPwd> userPwds = userPwdDao.selectByExample(userEx);
        List<UserVo> targetUsers =
                userPwds.parallelStream().map(UserVo::from).collect(Collectors.toList());
        log.info("targetUsers:" + targetUsers);
        return targetUsers;
    }

    private void generateUserFuzzySearchTrees() {
        long start = System.currentTimeMillis();
        Example example = new Example(UserPwd.class);
        example.createCriteria()
                .andEqualTo("status", String.valueOf(ConstantCommon.OPERATION_RESULT_FAILED));
        List<UserPwd> userPwdList = userPwdDao.selectByExample(example);
        List<String> uids =
                userPwdList.parallelStream().map(UserPwd::getUid).collect(Collectors.toList());
        List<String> names =
                userPwdList.parallelStream()
                        .map(UserPwd::getDisplayName)
                        .collect(Collectors.toList());
        long dbTime = System.currentTimeMillis();
        uidTree = new FuzzySearchTree(uids, false);
        long uidTreeTime = System.currentTimeMillis();
        nameTree = new FuzzySearchTree(names, true);
        long nameTreeTime = System.currentTimeMillis();
        log.info("dbTime:" + (dbTime - start));
        log.info("uidTreeTime:" + (uidTreeTime - dbTime));
        log.info("nameTreeTime:" + (nameTreeTime - uidTreeTime));
    }

    private List<DeptPersonVO> getTargetDepartments(String keyword, Map<String, AsDepart> deptMap) {
        if (StringUtils.isBlank(keyword)) {
            // 如果keyword为空，返回全量
            return departService.getDeptTree();
        }
        if (deptNameTree == null) {
            generateDeptNameFuzzySearchTrees();
        }
        List<String> targetDeptName = deptNameTree.search(keyword);
        if (targetDeptName.isEmpty()) {
            return Collections.emptyList();
        }
        Example example = new Example(AsDepart.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo(ConstantCommon.STATUS, StatusEnum.LIVE.getCode());
        if (!targetDeptName.isEmpty()) {
            criteria.andIn("bmmc", targetDeptName);
        }
        List<AsDepart> asDeparts = departMapper.selectByExample(example);
        Stream<DeptPersonVO> targetStream =
                asDeparts.parallelStream()
                        .map(
                                asDepart -> {
                                    DeptPersonVO deptPersonVO = new DeptPersonVO(asDepart);
                                    String deptName = deptPersonVO.getBmmc();
                                    deptPersonVO.setHighlight(findHighlightWord(keyword, deptName));
                                    return deptPersonVO;
                                });
        // 获取所有子部门
        Set<String> deptChildrenAll = new HashSet<>();
        asDeparts.forEach(
                asDepart -> {
                    Set<String> deptChildren = new HashSet<>();
                    String deptCode = asDepart.getBmdm();
                    departService.getBmChildren(deptCode, deptChildren, deptMap);
                    if (CollUtil.isNotEmpty(deptChildren)) {
                        deptChildrenAll.addAll(deptChildren);
                    }
                });
        // 将所有子部门加入到搜索结果中
        Stream<DeptPersonVO> childStream =
                deptChildrenAll.parallelStream().map(v -> new DeptPersonVO(deptMap.get(v)));
        Stream<DeptPersonVO> mergedStream = Stream.concat(targetStream, childStream);
        List<DeptPersonVO> targetDepartments =
                mergedStream.filter(Objects::nonNull).distinct().collect(Collectors.toList());
        log.info("targetDepartments:" + targetDepartments);
        return targetDepartments;
    }

    private static Set<String> findHighlightWord(String keyword, String originalWords) {
        return FuzzySearchTree.findMatchingSubstrings(originalWords, keyword);
    }

    /** 生成部门名称模糊搜索树 */
    private void generateDeptNameFuzzySearchTrees() {
        List<DeptPersonVO> deptPersonVOs = departService.getDeptTree();
        List<String> deptNames =
                deptPersonVOs.parallelStream()
                        .map(DeptPersonVO::getBmmc)
                        .collect(Collectors.toList());
        deptNameTree = new FuzzySearchTree(deptNames, false);
    }

    private static void constructPath(
            Map<String, AsDepart> deptMap,
            Map<String, DepartTreeNode> deptTreeNodeMap,
            String deptCode) {
        if (ConstantCommon.NO_DEPT.equals(deptCode)) {
            return;
        }
        if (!deptTreeNodeMap.containsKey(deptCode)) {
            //            log.info("当前处理过的部门map：" + deptTreeNodeMap);
            //            log.info("当前处理的部门编号：" + deptCode);
            AsDepart dept = deptMap.get(deptCode);
            if (dept == null) {
                log.error("从{}缓存中获取的map中，部门不存在，部门编号：{}", DEPART_MAP, deptCode);
                return;
            }
            DepartTreeNode node = new DepartTreeNode(dept);

            deptTreeNodeMap.put(deptCode, node);
            if (dept.getBmdm().equals(ROOT_DEPARTMENT_NUM)) {
                log.info("已经到达根节点，当前处理过的部门有：{}", deptTreeNodeMap.keySet());
                return;
            }
            String parentDeptCode = dept.getSjbmdm();
            if (deptMap.containsKey(parentDeptCode)) {
                if (!deptTreeNodeMap.containsKey(parentDeptCode)) {
                    constructPath(deptMap, deptTreeNodeMap, parentDeptCode);
                }
                DepartTreeNode parent = deptTreeNodeMap.get(parentDeptCode);
                parent.addChildren(node);
            } else {
                log.error(
                        "从{}缓存中获取的map中，上级部门不存在，当前部门编号：{}，上级部门编码：{}",
                        DEPART_MAP,
                        deptCode,
                        parentDeptCode);
            }
        }
    }

    private Map<String, AsDepart> getDeptMapFromCache() {
        String key = DEPART_MAP;
        Map<String, AsDepart> result = redisCommon.get(key);
        if (result == null) {
            Example deptEx = new Example(AsDepart.class);
            deptEx.createCriteria().andEqualTo(ConstantCommon.STATUS, StatusEnum.LIVE.getCode());
            List<AsDepart> asDeparts = departMapper.selectByExample(deptEx);
            result =
                    asDeparts.stream()
                            .collect(Collectors.toMap(AsDepart::getBmdm, Function.identity()));
            redisCommon.set(key, result, expireTime);
        }
        return result;
    }

    /**
     * 判断字符串是否是中文 只能检测出中文汉字不能检测中文标点
     *
     * @param str 字符串
     * @return true 是中文 false 不是中文
     */
    public static boolean isContainChinese(String str) {
        Pattern p =
                Pattern.compile(
                        "[\u4E00-\u9FA5|\\！|\\，|\\。|\\（|\\）|\\《|\\》|\\“|\\”|\\？|\\：|\\；|\\【|\\】]");
        Matcher m = p.matcher(str);
        return m.find();
    }

    @Scheduled(fixedDelay = 1000 * 60 * 10)
    public void updateFuzzySearchTree() {
        log.info("开始初始化全量部门树");
        long start = System.currentTimeMillis();
        Map<String, AsDepart> deptMap = getDeptMapFromCache();
        List<DeptPersonVO> targetDepartments = getTargetDepartments(null, deptMap);
        List<UserVo> targetUsers = getTargetUsers(null);
        Map<String, DepartTreeNode> deptTreeNodeMap = new HashMap<>(targetDepartments.size());
        targetDepartments.forEach(
                deptPersonVO -> constructPath(deptMap, deptTreeNodeMap, deptPersonVO.getBmdm()));
        targetUsers.parallelStream().forEach(insertUserIntoDeptTree(deptMap, deptTreeNodeMap));
        fullTree = deptTreeNodeMap.getOrDefault(ROOT_DEPARTMENT_NUM, new DepartTreeNode());
        log.info("初始化全量部门树完成，耗时：{}ms", System.currentTimeMillis() - start);

        log.info("开始生成部门名称模糊搜索树");
        long generateDeptNameFuzzySearchTrees = System.currentTimeMillis();
        generateDeptNameFuzzySearchTrees();
        log.info(
                "生成部门名称模糊搜索树完成，耗时：{}ms",
                System.currentTimeMillis() - generateDeptNameFuzzySearchTrees);

        log.info("开始生成人员名称模糊搜索树");
        long generateUserFuzzySearchTrees = System.currentTimeMillis();
        generateUserFuzzySearchTrees();
        log.info(
                "生成人员名称模糊搜索树完成，耗时：{}ms", System.currentTimeMillis() - generateUserFuzzySearchTrees);
    }

    private static Consumer<UserVo> insertUserIntoDeptTree(
            Map<String, AsDepart> deptMap, Map<String, DepartTreeNode> deptTreeNodeMap) {
        return user -> {
            String deptCode = user.getDepartmentNumber();
            constructPath(deptMap, deptTreeNodeMap, deptCode);
            DepartTreeNode node = deptTreeNodeMap.get(deptCode);
            if (node == null) {
                log.error("部门树节点缓存中找不到,deptCode:{},user:{}", deptCode, user);
                return;
            }
            node.addUser(user);
        };
    }
}
