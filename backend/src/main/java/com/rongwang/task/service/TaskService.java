package com.rongwang.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.auth.model.UserRole;
import com.rongwang.auth.session.LoginUser;
import com.rongwang.common.api.PageResponse;
import com.rongwang.common.exception.ApiException;
import com.rongwang.course.service.CourseService;
import com.rongwang.question.dto.QuestionDtos;
import com.rongwang.question.mapper.QuestionMapper;
import com.rongwang.question.model.Question;
import com.rongwang.question.service.QuestionService;
import com.rongwang.task.dto.TaskDtos.*;
import com.rongwang.task.mapper.TaskMapper;
import com.rongwang.task.model.Task;
import com.rongwang.task.model.TaskQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class TaskService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal MAX_SCORE = new BigDecimal("999999.99");
    private final TaskMapper mapper;
    private final CourseService courses;
    private final QuestionMapper bank;
    private final QuestionService questions;
    private final ObjectMapper json;
    public TaskService(TaskMapper mapper, CourseService courses, QuestionMapper bank,
                       QuestionService questions, ObjectMapper json) {
        this.mapper=mapper; this.courses=courses; this.bank=bank; this.questions=questions; this.json=json;
    }

    public PageResponse<TaskView> teacherList(LoginUser user, long courseId, int page, int size) {
        courses.teacherDetail(user, courseId);
        long offset=offset(page,size);
        return new PageResponse<>(mapper.teacherList(courseId,size,offset).stream().map(this::teacherView).toList(),
                mapper.teacherCount(courseId));
    }
    @Transactional
    public TaskView create(LoginUser user, long courseId, WriteTask body) {
        courses.teacherDetail(user,courseId);
        Task task=new Task();
        task.setCourseId(courseId); task.setStatus("DRAFT"); task.setTotalScore(BigDecimal.ZERO);
        apply(task,body);
        mapper.insert(task);
        return teacherView(task);
    }
    public TaskView teacherDetail(LoginUser user,long id) { return teacherView(owned(user,id,false)); }
    @Transactional
    public TaskView update(LoginUser user,long id,WriteTask body) {
        Task task=editable(user,id);
        apply(task,body);
        mapper.updateDraft(task);
        return teacherView(task);
    }
    @Transactional
    public TaskView select(LoginUser user,long id,SelectQuestions body) {
        Task task=editable(user,id);
        if(body==null || body.questions()==null || body.questions().size()>200)
            throw invalid("请传入不超过 200 道题目的有序数组");
        List<TaskQuestion> old=mapper.questions(id);
        Map<Long,TaskQuestion> oldById=new HashMap<>();
        for(TaskQuestion item:old) oldById.put(item.getId(),item);
        List<TaskQuestion> selected=new ArrayList<>();
        Set<Long> sources=new HashSet<>();
        Set<Long> draftIds=new HashSet<>();
        for(Selection item:body.questions()) {
            if(item==null) throw invalid("选题条目不能为空");
            Long sourceId=id(item.questionId());
            if(!sources.add(sourceId)) throw invalid("同一道原题不能在任务中重复选入");
            Question source=bank.selectById(sourceId);
            if(source==null || !task.getCourseId().equals(source.getCourseId()))
                throw invalid("只能选择本课程题库中的题目");
            TaskQuestion prior=null;
            if(item.taskQuestionId()!=null) {
                Long draftId=id(item.taskQuestionId());
                if(!draftIds.add(draftId)) throw invalid("草稿题目不能重复使用");
                prior=oldById.get(draftId);
                if(prior==null || !sourceId.equals(prior.getQuestionId()))
                    throw invalid("草稿题目不属于本任务或原题不匹配");
            }
            QuestionDtos.WriteRequest content=item.content();
            if(content==null) content=prior==null ? content(source) : content(prior);
            Question normalized=questions.normalizedDraft(content);
            TaskQuestion copy=copy(id,sourceId,selected.size()+1,normalized);
            selected.add(copy);
        }
        total(selected);
        // 完整校验后才替换；任一步写入失败由事务回滚。
        mapper.clearQuestions(id);
        selected.forEach(mapper::insertQuestion);
        return teacherView(task);
    }
    @Transactional
    public TaskView publish(LoginUser user,long id) {
        Task task=editable(user,id);
        if(task.getDeadlineAt()==null || !task.getDeadlineAt().isAfter(LocalDateTime.now(ZONE)))
            throw invalid("截止时间必须晚于服务器当前时间");
        List<TaskQuestion> selected=mapper.questions(id);
        if(selected.isEmpty()) throw invalid("发布前至少选择一道题");
        for(TaskQuestion q:selected) questions.normalizedDraft(content(q));
        BigDecimal score=total(selected);
        if(mapper.publish(id,score)!=1) throw locked();
        return teacherView(mapper.selectById(id));
    }
    @Transactional
    public TaskView copyTask(LoginUser user,long id) {
        Task original=owned(user,id,true);
        Task copy=new Task();
        copy.setCourseId(original.getCourseId());
        String suffix="（副本）";
        String title=original.getTitle();
        copy.setTitle(title.length()+suffix.length()>100 ? title.substring(0,100-suffix.length())+suffix : title+suffix);
        copy.setDescription(original.getDescription());
        copy.setDeadlineAt(original.getDeadlineAt());
        copy.setStatus("DRAFT"); copy.setTotalScore(BigDecimal.ZERO);
        mapper.insert(copy);
        for(TaskQuestion item:mapper.questions(id)) {
            TaskQuestion cloned=new TaskQuestion();
            cloned.setTaskId(copy.getId()); cloned.setQuestionId(item.getQuestionId());
            cloned.setPosition(item.getPosition()); cloned.setType(item.getType()); cloned.setStem(item.getStem());
            cloned.setOptionsJson(item.getOptionsJson()); cloned.setCorrectAnswerJson(item.getCorrectAnswerJson());
            cloned.setScore(item.getScore()); cloned.setExplanation(item.getExplanation());
            mapper.insertQuestion(cloned);
        }
        return teacherView(copy);
    }
    public PageResponse<StudentTaskView> studentList(LoginUser user,long courseId,int page,int size) {
        courses.studentDetail(user,courseId);
        long offset=offset(page,size);
        return new PageResponse<>(mapper.studentList(courseId,size,offset).stream()
                .map(task -> studentView(task,user.id())).toList(),mapper.studentCount(courseId));
    }
    public StudentTaskView studentDetail(LoginUser user,long id) {
        if(user.role()!=UserRole.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_FORBIDDEN","仅学生可访问");
        Task task=mapper.selectById(id);
        if(task==null || !"PUBLISHED".equals(task.getStatus())) throw notFound();
        courses.studentDetail(user,task.getCourseId());
        return studentView(task,user.id());
    }

    private Task owned(LoginUser user,long id,boolean lock) {
        if(user.role()!=UserRole.TEACHER) throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_FORBIDDEN","仅教师可操作任务");
        Task task=lock?mapper.lockTask(id):mapper.selectById(id);
        if(task==null) throw notFound();
        courses.teacherDetail(user,task.getCourseId());
        return task;
    }
    private Task editable(LoginUser user,long id) {
        Task task=owned(user,id,true);
        if(!"DRAFT".equals(task.getStatus())) throw locked();
        return task;
    }
    private void apply(Task task,WriteTask body) {
        if(body==null) throw invalid("请求体不能为空");
        if(body.title()!=null) task.setTitle(body.title().trim());
        if(task.getTitle()==null || task.getTitle().isBlank() || task.getTitle().length()>100)
            throw invalid("任务名称不能为空且最多 100 字符");
        if(body.description()!=null) task.setDescription(body.description());
        if(task.getDescription()!=null && task.getDescription().length()>10000)
            throw invalid("任务说明最多 10000 字符");
        if(body.deadlineAt()!=null) {
            try { task.setDeadlineAt(OffsetDateTime.parse(body.deadlineAt()).atZoneSameInstant(ZONE).toLocalDateTime()); }
            catch(DateTimeParseException exception) { throw invalid("截止时间须为带时区的 ISO 8601 时间"); }
        }
    }
    private TaskQuestion copy(long taskId,long sourceId,int position,Question q) {
        TaskQuestion result=new TaskQuestion();
        result.setTaskId(taskId); result.setQuestionId(sourceId); result.setPosition(position);
        result.setType(q.getType()); result.setStem(q.getStem()); result.setOptionsJson(q.getOptionsJson());
        result.setCorrectAnswerJson(q.getCorrectAnswerJson()); result.setScore(q.getScore());
        result.setExplanation(q.getExplanation()); return result;
    }
    private QuestionDtos.WriteRequest content(Question q) {
        return new QuestionDtos.WriteRequest(q.getType(),q.getStem(),readOptions(q.getOptionsJson()),
                readAnswer(q.getCorrectAnswerJson()),q.getScore(),q.getExplanation());
    }
    private QuestionDtos.WriteRequest content(TaskQuestion q) {
        return new QuestionDtos.WriteRequest(q.getType(),q.getStem(),readOptions(q.getOptionsJson()),
                readAnswer(q.getCorrectAnswerJson()),q.getScore(),q.getExplanation());
    }
    private List<QuestionDtos.Option> readOptions(String value) {
        try { return json.readValue(value,new TypeReference<List<QuestionDtos.Option>>(){}); }
        catch(JsonProcessingException e) { throw new IllegalStateException("题目选项 JSON 损坏",e); }
    }
    private List<String> readAnswer(String value) {
        try { return json.readValue(value,new TypeReference<List<String>>(){}); }
        catch(JsonProcessingException e) { throw new IllegalStateException("题目答案 JSON 损坏",e); }
    }
    private BigDecimal total(List<TaskQuestion> selected) {
        BigDecimal score=selected.stream().map(TaskQuestion::getScore).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(score.compareTo(MAX_SCORE)>0) throw invalid("任务总分超过 999999.99");
        return score;
    }
    private TaskView teacherView(Task task) {
        List<TaskQuestion> snapshots=mapper.questions(task.getId());
        List<TaskQuestionView> items=snapshots.stream()
                .map(q -> new TaskQuestionView(q.getId().toString(),q.getQuestionId().toString(),q.getPosition(),
                        q.getType(),q.getStem(),readOptions(q.getOptionsJson()),readAnswer(q.getCorrectAnswerJson()),
                        q.getScore().toPlainString(),q.getExplanation())).toList();
        return new TaskView(task.getId().toString(),task.getCourseId().toString(),task.getTitle(),task.getDescription(),
                time(task.getDeadlineAt()),task.getStatus(),time(task.getPublishedAt()),
                ("PUBLISHED".equals(task.getStatus()) ? task.getTotalScore() :
                        total(snapshots)).toPlainString(),items);
    }
    private StudentTaskView studentView(Task task,long studentId) {
        List<StudentQuestionView> items=mapper.questions(task.getId()).stream()
                .map(q -> new StudentQuestionView(q.getId().toString(),q.getPosition(),q.getType(),q.getStem(),
                        readOptions(q.getOptionsJson()),q.getScore().toPlainString())).toList();
        Long representative=mapper.representative(task.getId(),studentId);
        return new StudentTaskView(task.getId().toString(),task.getCourseId().toString(),task.getTitle(),
                task.getDescription(),time(task.getDeadlineAt()),task.getTotalScore().toPlainString(),
                items,representative==null?null:mapper.submissionScore(representative).toPlainString(),
                representative==null?null:representative.toString());
    }
    private String time(LocalDateTime value) { return value==null?null:value.atZone(ZONE).toOffsetDateTime().toString(); }
    private long offset(int page,int size) {
        if(page<1 || size<1 || size>100) throw invalid("page 至少为 1，size 为 1 到 100");
        return ((long)page-1)*size;
    }
    private Long id(String raw) {
        try { long result=Long.parseLong(raw); if(result>0) return result; }
        catch(Exception ignored) {}
        throw invalid("题目 ID 必须为正整数");
    }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TASK",message); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"TASK_NOT_FOUND","任务不存在或不可访问"); }
    private ApiException locked() { return new ApiException(HttpStatus.CONFLICT,"TASK_LOCKED","发布后任务已锁定"); }
}

