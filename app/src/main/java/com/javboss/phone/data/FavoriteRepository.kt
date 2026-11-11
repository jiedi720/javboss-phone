package com.javboss.phone.data

import com.javboss.phone.data.api.ApiClient
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteGroupNameRequest
import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.data.api.dto.RemoveFavoriteItemsRequest
import com.javboss.phone.data.api.dto.ReplaceFavoriteGroupsRequest

/**
 * 收藏夹仓库。服务端按实体类型分端点（Web 端 `/jav/{kind}-favorite-groups`）：
 * kind = jav | idol | studio | series；实体归属 entity = items | idols | studios | series。
 * 默认 jav / items，保证详情页、收藏夹页等旧调用不用改。
 */
class FavoriteRepository(private val apiClient: ApiClient) {

    companion object {
        const val KIND_JAV = "jav"
        const val KIND_IDOL = "idol"
        const val ENTITY_JAV = "items"
        const val ENTITY_IDOL = "idols"
    }

    suspend fun listGroups(kind: String = KIND_JAV): List<FavoriteGroupDto> =
        apiClient.api().listFavoriteGroupsOf(kind).items

    suspend fun createGroup(name: String, kind: String = KIND_JAV): FavoriteGroupDto =
        apiClient.api().createFavoriteGroupOf(kind, FavoriteGroupNameRequest(name))

    suspend fun renameGroup(id: Long, name: String, kind: String = KIND_JAV) =
        apiClient.api().renameFavoriteGroupOf(kind, id, FavoriteGroupNameRequest(name))

    suspend fun deleteGroup(id: Long, kind: String = KIND_JAV) =
        apiClient.api().deleteFavoriteGroupOf(kind, id)

    suspend fun listItems(groupId: Long, kind: String = KIND_JAV): List<FavoriteItemDto> =
        apiClient.api().listFavoriteGroupItemsOf(kind, groupId).items

    suspend fun removeItems(groupId: Long, entityIds: List<Long>, kind: String = KIND_JAV) =
        apiClient.api().removeFavoriteGroupItemsOf(kind, groupId, RemoveFavoriteItemsRequest(entityIds))

    /** 某实体当前所在的收藏夹 id 列表 */
    suspend fun selectedGroupIds(id: Long, entity: String = ENTITY_JAV): List<Long> =
        apiClient.api().listSelectedGroupsOf(entity, id).selectedGroupIds

    /** 全量替换某实体的收藏夹归属（勾选保存语义） */
    suspend fun replaceGroups(id: Long, groupIds: List<Long>, entity: String = ENTITY_JAV) =
        apiClient.api().replaceGroupsOf(entity, id, ReplaceFavoriteGroupsRequest(groupIds))
}
