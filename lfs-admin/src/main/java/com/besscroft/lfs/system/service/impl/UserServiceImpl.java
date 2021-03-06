package com.besscroft.lfs.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.besscroft.lfs.converter.UserConverterMapper;
import com.besscroft.lfs.dto.AuthUserExcelDto;
import com.besscroft.lfs.entity.AuthResource;
import com.besscroft.lfs.entity.AuthRole;
import com.besscroft.lfs.entity.AuthUser;
import com.besscroft.lfs.security.model.LFSUser;
import com.besscroft.lfs.system.repository.UserRepository;
import com.besscroft.lfs.system.service.MenuService;
import com.besscroft.lfs.system.service.ResourceService;
import com.besscroft.lfs.system.service.UserService;
import com.besscroft.lfs.security.utils.JWTUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author Bess Croft
 * @Time 2021/7/7 15:55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final ResourceService resourceService;
    private final JWTUtils jwtUtils;
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final MenuService menuService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ?????????DAO???????????????????????????
        AuthUser user = userRepository.findByUsername(username);
        // ?????????????????????????????????????????????????????????Spring Security????????????????????????
        if (user == null) {
            throw new UsernameNotFoundException("????????????????????????");
        }
        // ??????????????????
        List<AuthResource> resourceList = resourceService.getResourceList(user.getId());
        // ?????????????????? UserDetail ??????
        return new LFSUser(user, resourceList);
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        // ????????????????????????????????????
        try {
            UserDetails userDetails = loadUserByUsername(username);
            LOGGER.info("UserDetails:{}", JSONUtil.toJsonStr(userDetails));
            if(!passwordEncoder.matches(password,userDetails.getPassword())){
                throw new RuntimeException("???????????????");
            }
            if(!userDetails.isEnabled()){
                throw new RuntimeException("??????????????????");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            token = jwtUtils.generateToken(userDetails);
        } catch (AuthenticationException e) {
            LOGGER.warn("????????????:{}", e.getMessage());
        }
        return token;
    }

    @Override
    public AuthUser getCurrentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserName;
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            currentUserName = authentication.getName();
            log.info("currentUserName:{}", JSONUtil.toJsonStr(currentUserName));
        } else {
            throw new RuntimeException("???????????????token????????????");
        }
        return userRepository.findByUsername(currentUserName);
    }

    @Override
    public AuthUser getCurrentAdminByUserName(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getUserInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AuthUser currentAdmin = getCurrentAdminByUserName(username);
        Map<String, Object> data = new HashMap<>();
        data.put("username", currentAdmin.getUsername());
        data.put("menus", menuService.getMenuList(currentAdmin.getId()));
        data.put("icon", currentAdmin.getIcon());
        List<AuthRole> roleList = getRoleList(currentAdmin.getId());
        if(CollUtil.isNotEmpty(roleList)){
            List<String> roles = roleList.stream().map(AuthRole::getName).collect(Collectors.toList());
            data.put("roles",roles);
        }
        // ??????????????????
        setLoginTime(new Date(), currentAdmin.getId());
        return data;
    }

    @Override
    public List<AuthRole> getRoleList(Long userId) {
        return userRepository.findById(userId).get().getRoles();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setLoginTime(Date loginTime, Long id) {
        return userRepository.updateLoginTime(loginTime, id) > 0;
    }

    @Override
    public boolean logout(Long adminId) {
        return true;
    }

    @Override
    public Page<AuthUser> getUserPageList(Integer pageNum, Integer pageSize, String keyword) {
        return userRepository.findAll(PageRequest.of(Objects.equals(pageNum, 0) ? 0 : pageNum - 1, pageSize));
    }

    @Override
    public AuthUser getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(AuthUser authUser) {
        userRepository.save(authUser);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeSwitch(boolean flag, Long id) {
        int status;
        if (flag) {
            status = 1;
        } else {
            status = 0;
        }
        return userRepository.changeSwitch(status, id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delUser(Long id) {
        userRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addUser(AuthUser authUser) {
        // ???????????????????????????
        authUser.setCreateTime(LocalDateTime.now());
        // ?????????????????????????????????????????????
        authUser.setLoginTime(LocalDateTime.now());
        // ????????????
        authUser.setPassword(new BCryptPasswordEncoder().encode(authUser.getPassword()));
        // ??????????????????
        authUser.setDel(1);
        userRepository.save(authUser);
        return true;
    }

    @Override
    public void export(List<Long> ids, HttpServletResponse response) {
        List<AuthUser> userList = userRepository.findAllById(ids);
        if (CollUtil.isNotEmpty(userList)) {
            List<AuthUserExcelDto> excelDtos = UserConverterMapper.INSTANCE.authUserToAuthUserExcelListDto(userList);
            excelDtos.forEach(excelDto -> {
                String status = excelDto.getStatus();
                switch (status) {
                    case "0":
                        excelDto.setStatus("??????");
                        break;
                    case "1":
                        excelDto.setStatus("??????");
                        break;
                }
                String del = excelDto.getDel();
                switch (del) {
                    case "0":
                        excelDto.setDel("?????????");
                        break;
                    case "1":
                        excelDto.setDel("????????????");
                        break;
                }
            });
            try {
                // ???????????? ????????????????????? swagger ?????????????????????????????????????????????????????? postman
                response.setContentType("application/vnd.ms-excel");
                // ???????????????????????????
                response.setCharacterEncoding("utf-8");
                // ?????? URLEncoder.encode ???????????????????????? ????????? easyexcel ????????????
                String fileName = URLEncoder.encode("????????????", "UTF-8").replaceAll("\\+", "%20");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
                EasyExcel.write(response.getOutputStream(), AuthUserExcelDto.class).autoCloseStream(true).sheet("????????????").doWrite(excelDtos);
            } catch (IOException e) {
                log.error("excel ????????????.", e);
            }
        }
    }

}
