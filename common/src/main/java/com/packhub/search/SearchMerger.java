package com.packhub.search;

import com.packhub.modrinth.SearchResult;
import com.packhub.modrinth.model.ProjectSummary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把多条查询的结果合并成一页（中文搜索会拆成「翻译后的英文查询 + 原中文查询」多次请求）。
 *
 * <p>合并规则：按查询优先级轮流取（先保证第一条查询的排序不被冲淡），
 * 同一个项目只保留一次，最后按 limit 截断。</p>
 *
 * <p>纯逻辑，可离线单测。</p>
 */
public final class SearchMerger {

    private SearchMerger() {
    }

    /**
     * @param results 按优先级排好序的多个结果页
     * @param limit   最多返回多少条
     */
    public static SearchResult merge(List<SearchResult> results, int limit) {
        SearchResult merged = new SearchResult();
        if (results == null || results.isEmpty()) {
            return merged;
        }
        int maxLimit = limit > 0 ? limit : 20;
        Set<String> seen = new LinkedHashSet<>();
        int totalHits = 0;
        int offset = 0;
        int pageLimit = maxLimit;

        List<List<ProjectSummary>> pools = new ArrayList<>();
        for (SearchResult result : results) {
            if (result == null) {
                continue;
            }
            pools.add(result.hits);
            totalHits = Math.max(totalHits, result.totalHits);
            offset = Math.max(offset, result.offset);
            pageLimit = Math.max(pageLimit, result.limit);
        }

        // 按查询优先级顺序拼接：第一条查询的结果整体排在最前（它是翻译后的主查询，相关性最高），
        // 后面的查询只用来补充没出现过的项目。
        for (List<ProjectSummary> pool : pools) {
            for (ProjectSummary hit : pool) {
                if (merged.hits.size() >= maxLimit) {
                    break;
                }
                if (seen.add(key(hit))) {
                    merged.hits.add(hit);
                }
            }
            if (merged.hits.size() >= maxLimit) {
                break;
            }
        }

        merged.offset = offset;
        merged.limit = pageLimit;
        merged.totalHits = totalHits;
        return merged;
    }

    /** 去重用的键：优先项目 id，其次 slug，最后标题。 */
    public static String key(ProjectSummary project) {
        if (project == null) {
            return "";
        }
        if (project.projectId != null && !project.projectId.isBlank()) {
            return project.source.id() + ":" + project.projectId;
        }
        if (project.slug != null && !project.slug.isBlank()) {
            return project.source.id() + ":" + project.slug;
        }
        return project.source.id() + ":" + String.valueOf(project.title);
    }
}
