package com.teamlabtodolist.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.transaction.Transactional;

import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.teamlabtodolist.constraints.CreationResult;
import com.teamlabtodolist.constraints.TaskStatus;
import com.teamlabtodolist.dto.TodoTaskDto;
import com.teamlabtodolist.entity.RelationListTask;
import com.teamlabtodolist.entity.TodoList;
import com.teamlabtodolist.entity.TodoTask;
import com.teamlabtodolist.repository.TodoListRepository;
import com.teamlabtodolist.repository.TodoTaskRepository;

/**
 * タスクのサービス
 * @author mukaihiroto
 * 
 */
@Service
@Transactional
public class TodoTaskService {
    
    @Autowired
    TodoTaskRepository todoTaskRepository;
    
    @Autowired
    TodoListRepository todoListRepository;
    
    @Autowired
    RelationListTaskService relationListTaskService;
    
    @Autowired
    TodoListService todoListService;
    
    /**
     * 全件検索
     * @return
     */
    public List<TodoTask> findAll(){
        return todoTaskRepository.findAll();
    }
    
    /**
     * リストIDにひもづくタスクを取得
     * @param listId
     * @return
     * @throws Exception 
     */
    public TodoTask findTaskRelatedList(Integer listId) {
        return todoTaskRepository.findTaskByListId(listId);
    }
    
    /**
     * TodoTaskDtoの一覧を取得する
     * @param listId
     * @return
     */
    public List<TodoTaskDto> getTaskDtos(Integer listId){
        if(listId == null || listId <= 0)
            return Collections.emptyList();
        HashSet<Integer> relationList = new HashSet<>();
        //リストのIDを元にタスクを期限で昇順
        relationListTaskService.findByListId(listId).forEach(r -> relationList.add(r.getTaskId()));
        List<TodoTaskDto> todoTaskDtos = new ArrayList<TodoTaskDto>();
        List<TodoTask> todoTasks = new ArrayList<TodoTask>();
        if(relationList.isEmpty())
            return todoTaskDtos;
        todoTasks = todoTaskRepository.findByIdInOrderByCreatedDesc(relationList);
        if(!todoTasks.isEmpty())
            for(TodoTask todoTask : todoTasks)
                todoTaskDtos.add(new TodoTaskDto(todoTask));
        return todoTaskDtos;
    }
    
    /**
     * titleによるタスク検索
     * @param title
     * @return
     */
    public List<TodoTaskDto> searchTaskByTitle(String title){
        if(StringUtils.isEmpty(title))
            return Collections.emptyList(); 
        //titleによるタスク検索
        List<TodoTask> todoTasks = todoTaskRepository.findByTitleContainingOrderByCreatedDesc(title);
        if(todoTasks.isEmpty())
            return Collections.emptyList();
        //紐付けを検索するためのidList
        HashSet<Integer> taskIds = new HashSet<>();
        todoTasks.forEach(t->taskIds.add(t.getId()));
        if(taskIds.isEmpty())
            return Collections.emptyList();
        List<RelationListTask> relationListTasks = relationListTaskService.findAllByTaskId(taskIds);
        //リストを検索するためのidList
        HashSet<Integer> listIds = new HashSet<>();
        relationListTasks.forEach(r->listIds.add(r.getListId()));
        List<TodoList> todoLists = todoListRepository.findByIdIn(listIds);
        List<TodoTaskDto> todoTaskDtos = new ArrayList<TodoTaskDto>();
        //taskIdによるリスト検索
        for(TodoTask t : todoTasks)
            for(RelationListTask r  : relationListTasks)
                if(r.getTaskId().equals(t.getId()))
                    for(TodoList l : todoLists)
                        if(l.getId().equals(r.getListId())){
                            TodoTaskDto todoTaskDto = new TodoTaskDto(t);
                            todoTaskDto.setListId(l.getId());
                            todoTaskDto.setListTitle(l.getTitle());
                            todoTaskDtos.add(todoTaskDto);
                        }
        return todoTaskDtos;
    }
    
    /**
     * 完了したタスクの数を取得
     * @param listId
     * @return
     */
    public Integer countCompleteTasksByListId(Integer listId){
        if(listId == null || listId <= 0)
            return 0;
        return todoTaskRepository.countCompleteTasksByListId(listId) == null ? 0 : todoTaskRepository.countCompleteTasksByListId(listId);
    }
    
    /**
     * TODOタスクを作成する
     * @param title
     */
    public TodoTask createTodoTask(TodoTaskDto dto){
        if(dto == null)
            return null;
        String title = dto.getTaskTitle();
        if(StringUtils.isEmpty(title)|| title.codePointCount(0, title.length()) > 30)
            return null;
        if(dto.getTaskLimitDate() == null)
            return null;
        for(TodoTaskDto t : searchTaskByTitle(title))
            if(t.getTaskTitle().equals(title))
                return null;
        TodoTask todoTask = new TodoTask();
        todoTask.setTitle(title);
        todoTask.setStatusCd(dto.getStatusCd());
        todoTask.setLimitDate(dto.getTaskLimitDate());
        return todoTaskRepository.save(todoTask);
    }
    
    /**
     * バリデーションの結果
     * @param dto
     * @return
     */
    public List<CreationResult> validateTaskCreation(TodoTaskDto dto){
        List<CreationResult> result = new ArrayList<CreationResult>();
        if(dto == null)
            result.add(CreationResult.DTO_NULL);
        String title = dto.getTaskTitle();
        if(StringUtils.isEmpty(title))
            result.add(CreationResult.TITLE_EMPTY);
        if(title.codePointCount(0, title.length()) > 30)
            result.add(CreationResult.TITLE_OUT_OF_RANGE);
        if(dto.getTaskLimitDate() == null)
            result.add(CreationResult.LIMIT_DATE_EMPTY);
        for(TodoTaskDto t : searchTaskByTitle(title))
            if(t.getTaskTitle().equals(title))
                result.add(CreationResult.TITLE_DUOLICATION);
        if(result.isEmpty())
            result.add(CreationResult.CREATION_SUCCESS);
        return result;
    }
    
    /**
     * タスクのステータス更新
     * @param taskId
     */
    public void updateStatusCd(Integer taskId){
        TodoTask updateTodoTask = (taskId == null || taskId <= 0) ? null : todoTaskRepository.findOne(taskId);
        if (updateTodoTask == null)
            return;
        switch (TaskStatus.of(updateTodoTask.getStatusCd()).get()){
        //未完了->完了
        case NOT_YET:
            updateTodoTask.setStatusCd(TaskStatus.DONE.getStatusCd());
            break;
        //完了->未完了
        case DONE:
            updateTodoTask.setStatusCd(TaskStatus.NOT_YET.getStatusCd());
            break;
        default:
            return;
        }
        todoTaskRepository.save(updateTodoTask);
    }
    
    /**
     * タスク削除
     * @param listId
     * @param taskId
     */
    public void deleteTodoTask(Integer listId, Integer taskId){
        TodoTask deleteTodoTask = (taskId == null || taskId <= 0) ? null : todoTaskRepository.findOne(taskId);
        if(deleteTodoTask == null)
            return;
        if(listId == null || listId <= 0)
            return;
        todoTaskRepository.delete(taskId);
        relationListTaskService.deleteRelation(listId, taskId);
        return;
    }
    
}
