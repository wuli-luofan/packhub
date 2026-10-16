package com.packhub.modrinth;

import java.util.ArrayList;
import java.util.List;

/** 一次搜索请求的参数。 */
public final class SearchQuery {
    public String query = "";
    public ContentType type = ContentType.MOD;
    public String gameVersion = "";
    public String loader = "";
    public int limit = 20;
    public int offset = 0;
    public String index = "relevance";

    public SearchQuery() {
    }

    public SearchQuery query(String value) {
        this.query = value == null ? "" : value;
        return this;
    }

    public SearchQuery type(ContentType value) {
        this.type = value;
        return this;
    }

    public SearchQuery gameVersion(String value) {
        this.gameVersion = value == null ? "" : value;
        return this;
    }

    public SearchQuery loader(String value) {
        this.loader = value == null ? "" : value;
        return this;
    }

    public SearchQuery limit(int value) {
        this.limit = value;
        return this;
    }

    public SearchQuery offset(int value) {
        this.offset = value;
        return this;
    }

    public SearchQuery index(String value) {
        this.index = value == null || value.isBlank() ? "relevance" : value;
        return this;
    }

    public SearchQuery copy() {
        SearchQuery copy = new SearchQuery();
        copy.query = query;
        copy.type = type;
        copy.gameVersion = gameVersion;
        copy.loader = loader;
        copy.limit = limit;
        copy.offset = offset;
        copy.index = index;
        return copy;
    }

    /** 构造 Modrinth facets：外层 AND，内层 OR。 */
    public List<List<String>> facets(boolean filterLoader) {
        List<List<String>> facets = new ArrayList<>();
        List<String> types = new ArrayList<>();
        types.add("project_type:" + type.modrinthType());
        facets.add(types);
        if (gameVersion != null && !gameVersion.isBlank()) {
            List<String> versions = new ArrayList<>();
            versions.add("versions:" + gameVersion);
            facets.add(versions);
        }
        if (filterLoader && type.loaderSpecific() && loader != null && !loader.isBlank()) {
            List<String> loaders = new ArrayList<>();
            loaders.add("categories:" + loader);
            facets.add(loaders);
        }
        return facets;
    }
}
