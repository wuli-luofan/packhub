package com.packhub.modrinth;

import com.packhub.modrinth.model.ProjectSummary;

import java.util.ArrayList;
import java.util.List;

/** 一页搜索结果。 */
public final class SearchResult {
    public final List<ProjectSummary> hits = new ArrayList<>();
    public int offset;
    public int limit;
    public int totalHits;

    public boolean hasNextPage() {
        return offset + hits.size() < totalHits;
    }

    public boolean hasPreviousPage() {
        return offset > 0;
    }
}
