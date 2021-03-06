package com.oyyo.eduservice.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyyo.commonUtils.JwtUtils;
import com.oyyo.commonUtils.Resp;
import com.oyyo.eduservice.entity.EduCourse;
import com.oyyo.eduservice.entity.EduCourseDescription;
import com.oyyo.eduservice.entity.EduTeacher;
import com.oyyo.eduservice.feign.ServiceOrderClient;
import com.oyyo.eduservice.mapper.EduCourseMapper;
import com.oyyo.eduservice.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyyo.eduservice.vo.*;
import com.oyyo.serviceBase.handler.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 课程 服务实现类
 * </p>
 *
 * @author oy
 * @since 2020-06-27
 */
@Service
@Slf4j
public class EduCourseServiceImpl extends ServiceImpl<EduCourseMapper, EduCourse> implements EduCourseService {

    @Autowired
    private EduCourseDescriptionService descriptionService;
    @Autowired
    private EduCourseMapper courseMapper;
    @Autowired
    private EduVideoService videoService;
    @Autowired
    private EduChapterService chapterService;
    @Autowired
    private EduTeacherService teacherService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ServiceOrderClient orderClient;


    /**
     * 添加课程
     * @param courseInfoVO
     * @return
     */
    @Override
    @Transactional
    public String addCourseInfo(CourseInfoVO courseInfoVO) {

        //添加课程表
        EduCourse eduCourse = new EduCourse();
        BeanUtils.copyProperties(courseInfoVO,eduCourse);

        boolean saveCourseFlag = this.save(eduCourse);
        if (!saveCourseFlag) {
            log.error("添加课程失败");
            throw new BaseException(20002,"添加课程失败！");
        }
        log.info("添加课程成功");
        //添加课程简介表
        //课程id
        String courseId = eduCourse.getId();

        EduCourseDescription eduCourseDescription = new EduCourseDescription();
        eduCourseDescription.setDescription(courseInfoVO.getDescription());
        eduCourseDescription.setId(courseId);
        boolean saveDescFlag = descriptionService.save(eduCourseDescription);
        if (!saveDescFlag) {
            log.error("添加课程描述失败");
            throw new BaseException(20002,"添加课程描述失败！");
        }
        log.info("添加课程描述成功");
        return courseId;
    }

    /**
     * 根据id查询课程信息
     * @param courseId
     * @return
     */
    @Override
    public CourseInfoVO queryCourseInfoByCourseId(String courseId) {

        CourseInfoVO courseInfoVO = new CourseInfoVO();

        //查询课程表
        EduCourse eduCourse = this.getById(courseId);
        //查询详细描述表
        EduCourseDescription description = descriptionService.getById(courseId);
        BeanUtils.copyProperties(eduCourse,courseInfoVO);
        courseInfoVO.setDescription(description.getDescription());

        return courseInfoVO;
    }

    /**
     * 修改课程
     * @param courseInfoVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourseInfo(CourseInfoVO courseInfoVO) {

        //修改课程表
        EduCourse eduCourse = new EduCourse();
        BeanUtils.copyProperties(courseInfoVO,eduCourse);
        boolean updateCourse = this.updateById(eduCourse);

        if (!updateCourse) {
            throw new BaseException(20001, "修改课程失败！");
        }

        //修改课程详情表
        EduCourseDescription description = new EduCourseDescription();
        BeanUtils.copyProperties(courseInfoVO,description);
        boolean updateDescription = descriptionService.updateById(description);
        if (!updateDescription) {
            throw new BaseException(20001, "修改课程详情失败！");
        }
    }

    /**
     * 根据课程id查询 最终发布信息
     * @param id
     * @return
     */
    @Override
    public CoursePublishVO queryPublishCourseInfo(String id) {
        CoursePublishVO publishVO = courseMapper.queryPublishVO(id);

        return publishVO;

    }




    /**
     * 分页查询讲师列表
     * @param current
     * @param limit
     * @return
     */
    @Override
    public Map queryCourseByPage(Long current, Long limit, CourseQueryVO courseQueryVO) {

        Page<EduCourse> pageCourse = new Page<>(current, limit);
        QueryWrapper<EduCourse> wrapper = new QueryWrapper<>();
        if (!StringUtils.isEmpty(courseQueryVO.getTitle())) {
            wrapper.like("title", courseQueryVO.getTitle());
        }
        if (courseQueryVO.getStatus() != null){
            wrapper.eq("status", courseQueryVO.getStatus());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getBegin())) {
            wrapper.ge("gmt_create", courseQueryVO.getBegin());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getEnd())) {
            wrapper.le("gmt_create", courseQueryVO.getEnd());
        }
        wrapper.orderByDesc("gmt_modified");
        Page<EduCourse> coursePage = page(pageCourse, wrapper);
        long total = coursePage.getTotal();
        List<EduCourse> courses = coursePage.getRecords();
        Map map = new HashMap(2);
        map.put("total", total);
        map.put("courseList", courses);

        return map;
    }

    /**
     * 根据id删除课程信息
     * @return
     */
    @Override
    @Transactional
    public boolean deleteCourseInfo(String courseId) {

        // 根据课程 id 删除 小节
        boolean delVideoFlag = videoService.deleteVideoByCourseId(courseId);

        // 删除章节
        boolean delChapterFlag = chapterService.deleteChapterByCourseId(courseId);

        //删除描述
        boolean delDescFlag = descriptionService.removeById(courseId);

        //删除课程
        boolean delCourseFlag = this.removeById(courseId);
        if (!delVideoFlag || !delChapterFlag || !delDescFlag || !delCourseFlag) {
            throw new BaseException(20001, "删除课程失败！");
        }
        return true;
    }

    /**
     * 发布课程
     * @param id
     */
    @Override
    public void publishCourse(String id) {
        EduCourse eduCourse = new EduCourse();
        eduCourse.setId(id);
        eduCourse.setStatus("Normal");
        boolean update = this.updateById(eduCourse);
        if (update) {
            log.info("课程发布成功！");
        }else {
            throw new BaseException(20001, "课程发布失败");
        }
    }

    /**
     * 查询 前 8 条热门 课程，4条名师
     * @return
     */
    @Override
    public Map<String, Object> queryHotTeacherAndCourse() {

        String courseList1 = redisTemplate.opsForValue().get("courseList");
        String teacherList1 = redisTemplate.opsForValue().get("teacherList");

        if (!StringUtils.isEmpty(courseList1) && !StringUtils.isEmpty(teacherList1)) {
            List<EduCourse> courses = JSON.parseArray(courseList1, EduCourse.class);
            List<EduTeacher> EduTeacher = JSON.parseArray(teacherList1, EduTeacher.class);
            Map<String, Object> map = new HashMap<>();
            map.put("hotCourse", courses);
            map.put("hotTeacher", EduTeacher);

            return map;
        }
        log.info("查询 8 条热门课程");
        QueryWrapper<EduCourse> courseQueryWrapper = new QueryWrapper<>();
        courseQueryWrapper.last("limit 8");
        courseQueryWrapper.orderByDesc("view_count");
        List<EduCourse> courseList = this.list(courseQueryWrapper);
        log.info("查询4条名师");
        QueryWrapper<EduTeacher> teacherWrapper = new QueryWrapper<>();
        teacherWrapper.last("limit 4");
        teacherWrapper.orderByAsc("sort");
        List<EduTeacher> teacherList = teacherService.list(teacherWrapper);

        redisTemplate.opsForValue().set("courseList", JSON.toJSONString(courseList));
        redisTemplate.opsForValue().set("teacherList", JSON.toJSONString(teacherList));

        Map<String, Object> map = new HashMap<>();
        map.put("hotCourse", courseList);
        map.put("hotTeacher", teacherList);

        return map;
    }

    /**
     * 条件查询课程
     * @return
     */
    @Override
    public Map<String, Object> queryCourseByProtal(Long current, Long limit, CourseInfoQueryVO courseQueryVO) {
        Page<EduCourse> coursePage = new Page<>(current, limit);

        QueryWrapper<EduCourse> wrapper = new QueryWrapper<>();
        if (!StringUtils.isEmpty(courseQueryVO.getSubjectParentId())) {
            wrapper.eq("subject_parent_id", courseQueryVO.getSubjectParentId());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getSubjectId())) {
            wrapper.eq("subject_id", courseQueryVO.getSubjectParentId());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getBuyCountSort())) {
            wrapper.orderByDesc("buy_count", courseQueryVO.getBuyCountSort());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getGmtCreateSort())) {
            wrapper.orderByDesc("gmt_create", courseQueryVO.getGmtCreateSort());
        }
        if (!StringUtils.isEmpty(courseQueryVO.getPriceSort())) {
            wrapper.orderByDesc("price", courseQueryVO.getPriceSort());
        }

        Page<EduCourse> page = this.page(coursePage, wrapper);
        Map map = new HashMap();
        map.put("total", page.getTotal());
        map.put("courseList", page.getRecords());

        return map;
    }

    /**
     * 查询课程详情
     * @param courseId
     * @param request
     * @return
     */
    @Override
    public Map<String, Object> queryCourseInfoAndTeacher(String courseId, HttpServletRequest request) {
        List<ChapterAndVideoVO> chapterVideo = chapterService.queryChapterVideo(courseId);
        CourseWebVo courseWebVo = courseMapper.queryCourseInfoAndTeacher(courseId);
        Map<String, Object> map = new HashMap<>();
        map.put("chapters", chapterVideo);
        map.put("courseWebVo", courseWebVo);
        //根据 课程id 和 用户id 远程调用查询 课程是否购买
        String memberId = JwtUtils.getMemberIdByJwtToken(request);
        if (!StringUtils.isEmpty(memberId)) {
            Resp resp = orderClient.courseIsBuy(courseId, memberId);
            map.put("isBuy", resp.getData().get("isBuy"));
        }
        return map;
    }
}
