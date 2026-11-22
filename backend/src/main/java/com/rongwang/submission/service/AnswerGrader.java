package com.rongwang.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rongwang.common.exception.ApiException;
import com.rongwang.task.model.TaskQuestion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class AnswerGrader {
    private final ObjectMapper json;
    public AnswerGrader(ObjectMapper json) {this.json=json;}
    public record Grade(List<String> selected,boolean correct,BigDecimal earned) {}
    private record Option(String key,String text) {}
    public Grade grade(TaskQuestion q,List<String> answer) {
        List<String> selected=answer==null?List.of():answer;
        List<Option> options=read(q.getOptionsJson(),new TypeReference<>(){});
        List<String> standard=read(q.getCorrectAnswerJson(),new TypeReference<>(){});
        Set<String> allowed=new HashSet<>();
        options.forEach(o->allowed.add(o.key()));
        Set<String> chosen=new HashSet<>();
        for(String key:selected) {
            if(key==null || !allowed.contains(key) || !chosen.add(key))
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","答案包含重复或未知选项");
        }
        if(!"MULTIPLE".equals(q.getType()) && selected.size()>1)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","单选和判断题最多选择一项");
        boolean correct=!chosen.isEmpty() && chosen.equals(new HashSet<>(standard));
        return new Grade(List.copyOf(selected),correct,correct?q.getScore():BigDecimal.ZERO);
    }
    private <T>T read(String value,TypeReference<T> type) {
        try {return json.readValue(value,type);}
        catch(JsonProcessingException e) {throw new IllegalStateException("任务快照 JSON 损坏",e);}
    }
}

