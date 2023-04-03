package com.cestc.dc.apihandler.service;


import com.cestc.dc.common.commonBean.ResultVO;
import com.cestc.dc.repository.domain.entity.user.AsDepart;
import com.cestc.dc.repository.domain.entity.user.DepartTreeNode;
import com.cestc.dc.repository.domain.vo.DeptInfoVo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DepartService {

    ResultVO getDeptTree();

    Set<String> getDeptParents(String deptNum);

    List<DeptInfoVo> getDeptInfo(List<String> asList);

    void getBmChildren(String deptNum, Set<String> bmChildren, Map<String, AsDepart> departMap);

    /**
     * 模糊查询部门或者人员
     * @param keyword 查询的关键字
     * @return 部门树根节点
     */
    DepartTreeNode fuzzySearchTree(String keyword);
}
