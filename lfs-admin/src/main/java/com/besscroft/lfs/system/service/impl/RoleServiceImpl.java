package com.besscroft.lfs.system.service.impl;

import com.besscroft.lfs.entity.AuthRole;
import com.besscroft.lfs.system.repository.RoleRepository;
import com.besscroft.lfs.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @Author Bess Croft
 * @Time 2021/7/8 18:09
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    public List<AuthRole> listAll() {
        return roleRepository.findAll();
    }

    @Override
    public Page<AuthRole> getRolePageList(Integer pageNum, Integer pageSize, String keyword) {
        return roleRepository.findAll(PageRequest.of(Objects.equals(pageNum, 0) ? 0 : pageNum - 1, pageSize));
    }

    @Override
    public AuthRole getRoleById(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addRole(AuthRole authRole) {
        // 假删除：0->删除状态；1->可用状态
        authRole.setDel(1);
        // 设置时间
        authRole.setCreateTime(LocalDateTime.now());
        roleRepository.save(authRole);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRole(AuthRole authRole) {
        // 设置时间
        authRole.setCreateTime(LocalDateTime.now());
        roleRepository.save(authRole);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delRoleById(List<Long> ids) {
        roleRepository.deleteAllByIdInBatch(ids);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeSwitch(boolean status, Long id) {
        if (status) {
            return roleRepository.changeSwitch(1, id) > 0;
        }
        return roleRepository.changeSwitch(0, id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRoleById(Long userId, Long roleId) {
        // 先删除原有的
        int i = roleRepository.deleteUserRoleRelationById(userId);
        if (i > 0) {
            return roleRepository.insertUserRoleRelation(userId, roleId) > 0;
        }
        return true;
    }

}
