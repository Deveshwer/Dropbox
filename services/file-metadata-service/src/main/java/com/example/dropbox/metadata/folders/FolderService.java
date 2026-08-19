package com.example.dropbox.metadata.folders;

import com.example.dropbox.metadata.common.ResourceType;
import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.files.FileSummary;
import com.example.dropbox.metadata.shares.ShareRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.dropbox.metadata.shares.PermissionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import com.example.dropbox.metadata.common.AuditEventService;
import com.example.dropbox.metadata.common.SyncAudienceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.dropbox.metadata.files.FileService;
import java.util.Comparator;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;
    private final PermissionService permissionService;
    private final ShareRepository shareRepository;
    private final AuditEventService auditEventService;
    private final FileService fileService;
    private final SyncAudienceService syncAudienceService;

    public FolderResponse create(CreateFolderRequest request, UUID ownerId) {
        Folder parentFolder = null;
        if(request.parentFolderId() != null) {
            parentFolder = folderRepository.findById(request.parentFolderId())
            .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));

            if (!permissionService.canWriteFolder(parentFolder.getId(), ownerId)) {
                throw new ForbiddenOperationException("You are not allowed to create a folder in this parent folder");
            }
        }
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(request.name());
        folder.setParentFolderId(request.parentFolderId());
        folder.setOwnerId(ownerId);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());

        Folder saved = folderRepository.save(folder);
        auditEventService.recordEvent(
            "FOLDER_CREATED",
            "FOLDER",
            saved.getId(),
            ownerId,
            "name=" + saved.getName()
        );
        return toFolderResponse(saved);
    }

    public FolderChildrenResponse getChildren(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
        .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (!permissionService.canReadFolder(folderId, userId)) {
            throw new ForbiddenOperationException("You are not allowed to access this folder");
        }
        List<FolderResponse> folders = folderRepository.findByParentFolderId(folderId)
                                        .stream()
                                        .filter(child  -> child.getDeletedAt() == null)
                                        .map(this::toFolderResponse)
                                        .toList();

        List<FileSummary> files = fileRecordRepository.findByFolderId(folderId)
                                        .stream()
                                        .filter(child -> child.getDeletedAt() == null)
                                        .map(this::toFileSummary)
                                        .toList();
        return new FolderChildrenResponse(folders, files);
    }

    public FolderResponse renameFolder(UUID folderId, RenameFolderRequest request, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!folder.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("You are not allowed to rename this folder");
        }

        folder.setName(request.name());
        folder.setUpdatedAt(Instant.now());

        Folder saved = folderRepository.save(folder);
        return toFolderResponse(saved);
    }

    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    public FolderResponse moveFolder(UUID folderId, MoveFolderRequest moveFolderRequest, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        Folder targetParent = folderRepository.findById(moveFolderRequest.targetParentFolderId())
              .orElseThrow(() -> new ResourceNotFoundException("Target folder not found"));

        if (!folder.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("You are not allowed to move this folder");
        }

        if(!permissionService.canWriteFolder(targetParent.getId(), userId)) {
            throw new ForbiddenOperationException("You are not allowed to move the folder to this location");
        }

        if(folder.getId().equals(targetParent.getId())) {
            throw new IllegalArgumentException("Folder cannot be moved to itself");
        }

        validateNoCycle(folder.getId(), targetParent.getId());

        folder.setParentFolderId(targetParent.getId());
        folder.setUpdatedAt(Instant.now());

        Folder saved = folderRepository.save(folder);

        return toFolderResponse(saved);
    }

    private void validateNoCycle(UUID folderId, UUID parentFolderId) {
        UUID curId = parentFolderId;
        while(curId !=  null) {
            if(folderId.equals(curId)) {
                throw new IllegalArgumentException("You are not allowed to move it to your descendents");
            }

            Folder folder = folderRepository.findById(curId)
                            .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

            curId = folder.getParentFolderId();
        }
    }


    @Caching(evict = {
      @CacheEvict(value = "folderPermissions", allEntries = true),
      @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void deleteFolder(UUID folderId, UUID userId) {
        Folder root = folderRepository.findById(folderId)
              .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (root.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!root.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("You are not allowed to delete this folder");
        }

        List<Folder> subtree = collectFolderSubtree(root);

        for (Folder folder : subtree) {
            for (FileRecord file : fileRecordRepository.findByFolderId(folder.getId())) {
                if (file.getDeletedAt() == null) {
                    fileService.deleteFile(file.getId(), userId);
                }
            }
        }

        Instant now = Instant.now();
        for (Folder folder : subtree) {
            if (folder.getDeletedAt() == null) {
                Set<UUID> syncAudience = syncAudienceService.resolveCurrentReadersForFolder(folder.getId());
                folder.setDeletedAt(now);
                folder.setUpdatedAt(now);
                folderRepository.save(folder);

                auditEventService.recordEvent(
                    "FOLDER_SOFT_DELETED",
                    ResourceType.FOLDER.name(),
                    folder.getId(),
                    userId,
                    "name=" + folder.getName(),
                    syncAudience
                );
            }
        }
    }

    @Caching(evict = {
    @CacheEvict(value = "folderPermissions", allEntries = true),
    @CacheEvict(value = "filePermissions", allEntries = true)
    })
    public FolderResponse restoreFolder(UUID folderId, UUID userId) {
        Folder root = folderRepository.findById(folderId)
              .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (!root.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("You are not allowed to restore this folder");
        }

        if (root.getDeletedAt() == null) {
            throw new IllegalArgumentException("Folder is not deleted");
        }

        List<Folder> subtree = collectFolderSubtree(root);
        Instant now = Instant.now();

        for (Folder folder : subtree) {
            if (folder.getDeletedAt() != null) {
                folder.setDeletedAt(null);
                folder.setUpdatedAt(now);
                folderRepository.save(folder);

                auditEventService.recordEvent(
                    "FOLDER_RESTORED",
                    ResourceType.FOLDER.name(),
                    folder.getId(),
                    userId,
                    "name=" + folder.getName()
                );
            }
        }

        for (Folder folder : subtree) {
            for (FileRecord file : fileRecordRepository.findByFolderId(folder.getId())) {
                if (file.getDeletedAt() != null) {
                    fileService.restoreFile(file.getId(), userId);
                }
            }
        }

        return toFolderResponse(root);
    }

    public List<FolderResponse> listDeletedFolders(UUID userId) {
      return folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId)
              .stream()
              .map(this::toFolderResponse)
              .toList();
    }

    @Caching(evict = {
    @CacheEvict(value = "folderPermissions", allEntries = true),
    @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void permanentlyDeleteFolder(UUID folderId, UUID userId) {
        Folder root = folderRepository.findById(folderId)
              .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (!root.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("You are not allowed to permanently delete this folder");
        }

        if (root.getDeletedAt() == null) {
            throw new IllegalArgumentException("Folder is not deleted");
        }

        permanentlyDeleteFolderSubtree(root, userId, null);
    }

    @Caching(evict = {
    @CacheEvict(value = "folderPermissions", allEntries = true),
    @CacheEvict(value = "filePermissions", allEntries = true)
    })
    @Transactional
    public void emptyTrash(UUID userId) {
        List<Folder> deletedFolders = folderRepository.findByOwnerIdAndDeletedAtIsNotNull(userId);

        for (Folder folder : deletedFolders) {
            if (hasDeletedAncestor(folder)) {
                continue;
            }
            permanentlyDeleteFolderSubtree(folder, userId, "emptyTrash");
        }
    }

    private FolderResponse toFolderResponse(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentFolderId(),
                folder.getOwnerId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    public FolderResponse getFolder(UUID folderId, UUID userId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!permissionService.canReadFolder(folderId, userId)) {
            throw new ForbiddenOperationException("You are not allowed to access this folder");
        }

        return toFolderResponse(folder);
    }

    public SearchResponse searchChildFolders(UUID folderId, UUID userId, String q, int page, int size) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!permissionService.canReadFolder(folderId, userId)) {
            throw new ForbiddenOperationException("You are not allowed to access this folder");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }

        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        String normalizedQuery = q == null ? null : q.trim();

        Page<Folder> resultPage;
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            resultPage = folderRepository.findByParentFolderIdAndDeletedAtIsNull(folderId, pageable);
        } else {
            resultPage = folderRepository.findByParentFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    folderId,
                    normalizedQuery,
                    pageable
            );
        }

        return new SearchResponse(
                resultPage.getContent().stream().map(this::toSearchResultItem).toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext()
        );
    }

    public SearchResponse searchChildFiles(UUID folderId, UUID userId, String q, int page, int size) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!permissionService.canReadFolder(folderId, userId)) {
            throw new ForbiddenOperationException("You are not allowed to access this folder");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }

        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        String normalizedQuery = q == null ? null : q.trim();

        Page<FileRecord> resultPage;
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            resultPage = fileRecordRepository.findByFolderIdAndDeletedAtIsNull(folderId, pageable);
        } else {
            resultPage = fileRecordRepository.findByFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    folderId,
                    normalizedQuery,
                    pageable
            );
        }

        return new SearchResponse(
                resultPage.getContent().stream().map(this::toSearchResultItem).toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.hasNext()
        );
    }

    private List<Folder> collectFolderSubtree(Folder root) {
        List<Folder> folders = new ArrayList<>();
        collectFolderSubtree(root, folders);
        return folders;
    }

    private void collectFolderSubtree(Folder folder, List<Folder> folders) {
        folders.add(folder);
        for (Folder child : folderRepository.findByParentFolderId(folder.getId())) {
            collectFolderSubtree(child, folders);
        }
    }

    private boolean hasDeletedAncestor(Folder folder) {
        UUID parentId = folder.getParentFolderId();
        while (parentId != null) {
            Folder parent = folderRepository.findById(parentId).orElse(null);
            if (parent == null) {
                return false;
            }
            if (parent.getDeletedAt() != null) {
                return true;
            }
            parentId = parent.getParentFolderId();
        }
        return false;
    }

    private void permanentlyDeleteFolderSubtree(Folder root, UUID userId, String source) {
        List<Folder> subtree = collectFolderSubtree(root);

        for (int i = subtree.size() - 1; i >= 0; i--) {
            Folder folder = subtree.get(i);
            Set<UUID> syncAudience = syncAudienceService.resolveCurrentReadersForFolder(folder.getId());

            for (FileRecord file : fileRecordRepository.findByFolderId(folder.getId())) {
                if (file.getDeletedAt() == null) {
                    file.setDeletedAt(Instant.now());
                    file.setUpdatedAt(Instant.now());
                    fileRecordRepository.save(file);
                }
                fileService.permanentlyDeleteFile(file.getId(), userId);
            }

            shareRepository.deleteByResourceTypeAndResourceId(ResourceType.FOLDER.name(), folder.getId());
            folderRepository.delete(folder);

            String details = "name=" + folder.getName();
            if (source != null) {
                details += ",source=" + source;
            }

            auditEventService.recordEvent(
                "FOLDER_PERMANENTLY_DELETED",
                ResourceType.FOLDER.name(),
                folder.getId(),
                userId,
                details,
                syncAudience
            );
        }
    }


    private SearchResultItem toSearchResultItem(FileRecord file) {
        return new SearchResultItem(
                file.getId(),
                file.getName(),
                ResourceType.FILE.name(),
                file.getFolderId(),
                file.getOwnerId(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }


    private SearchResultItem toSearchResultItem(Folder folder) {
      return new SearchResultItem(
              folder.getId(),
              folder.getName(),
              ResourceType.FOLDER.name(),
              folder.getParentFolderId(),
              folder.getOwnerId(),
              folder.getCreatedAt(),
              folder.getUpdatedAt()
      );
    }

    private FileSummary toFileSummary(FileRecord file) {
        return new FileSummary(
                file.getId(),
                file.getName(),
                file.getFolderId(),
                file.getOwnerId(),
                file.getCurrentVersionId(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    public SearchResponse searchAllChildren(UUID folderId, UUID userId, String q, int page, int size) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));

        if (folder.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Folder not found");
        }

        if (!permissionService.canReadFolder(folderId, userId)) {
            throw new ForbiddenOperationException("You are not allowed to access this folder");
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }

        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        String normalizedQuery = q == null ? null : q.trim();

        List<Folder> folders;
        List<FileRecord> files;

        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            folders = folderRepository.findByParentFolderIdAndDeletedAtIsNull(folderId);
            files = fileRecordRepository.findByFolderIdAndDeletedAtIsNull(folderId);
        } else {
            folders = folderRepository.findByParentFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    folderId,
                    normalizedQuery
            );
            files = fileRecordRepository.findByFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    folderId,
                    normalizedQuery
            );
        }

        List<SearchResultItem> items = new ArrayList<>();
        items.addAll(folders.stream().map(this::toSearchResultItem).toList());
        items.addAll(files.stream().map(this::toSearchResultItem).toList());

        List<SearchResultItem> sortedItems = items.stream()
                .sorted(
                        Comparator.comparing(SearchResultItem::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(SearchResultItem::resourceType)
                                .thenComparing(SearchResultItem::id)
                )
                .toList();

        int totalElements = sortedItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;

        if (fromIndex >= totalElements) {
            return new SearchResponse(
                    List.of(),
                    page,
                    size,
                    totalElements,
                    totalPages,
                    false
            );
        }

        int toIndex = Math.min(fromIndex + size, totalElements);
        List<SearchResultItem> pageItems = sortedItems.subList(fromIndex, toIndex);
        boolean hasNext = page + 1 < totalPages;

        return new SearchResponse(
                pageItems,
                page,
                size,
                totalElements,
                totalPages,
                hasNext
        );
    }

    public FolderChildrenResponse getRootChildren(UUID userId) {
        List<FolderResponse> folders = folderRepository
                .findByOwnerIdAndParentFolderIdIsNullAndDeletedAtIsNull(userId)
                .stream()
                .map(this::toFolderResponse)
                .toList();

        return new FolderChildrenResponse(folders, List.of());
    }


}


/*

Q & A for this class:


  ## 1. Why stream().map(...).toList() if repository already returns List?

  The repository does return a List, yes.

  But it returns:

  - List<Folder>
  - List<FileRecord>

  Your response needs:

  - List<FolderResponse>
  - List<FileSummary>

  So the stream is not for “making it a list again.”
  It is for transforming each item from one type into another.

  Example:

  List<Folder>

  must become:

  List<FolderResponse>

  That is why we do:

  folderRepository.findByParentFolderId(folderId)
      .stream()
      .map(this::toFolderResponse)
      .toList();

  Meaning:

  1. get list of Folder
  2. go through each folder
  3. convert each one to FolderResponse
  4. collect them back into a list

  Without mapping, the types do not match.

  If you returned entities directly, then yes, you would not need this transformation. But that leads to the second question.

  ———

  ## 2. Why not return the entity directly if fields are currently the same?

  Because same today does not mean should stay coupled forever.

  Right now your entity and DTO look similar, but they represent different concerns:

  - Entity = database model
  - DTO = API contract

  That separation matters even if the fields currently overlap 1:1.

  ### Why not send entity directly?

  Because entities are internal persistence objects. If you expose them directly:

  - API becomes coupled to DB schema
  - internal fields can leak later
  - refactoring DB becomes harder
  - lazy-loading or ORM-related serialization issues can happen later
  - you lose control over API shape

  ### Example of future divergence

  Today:

  Folder entity:
  id, name, parentFolderId, ownerId, createdAt, updatedAt

  Response DTO:

  id, name, parentFolderId, ownerId, createdAt, updatedAt

  Looks identical.

  Later, you may want API response like:

  id, name, parentFolderId, isRoot, itemCount

  or maybe hide ownerId.

  Or later your entity may gain internal fields like:

  - deletedAt
  - internalPath
  - lockVersion
  - tenantId

  You may not want those exposed in the API.

  If you return entities directly, any such change can accidentally change your public API.

  DTOs prevent that.

  ———

  ## Why mapping is still worth it even now

  This line:

  Folder saved = folderRepository.save(folder);
  return toFolderResponse(saved);

  looks redundant today, but it establishes a clean boundary.

  The service says:

  - persistence is internal
  - response format is explicit

  That is good backend design.

  ———

  ## Mental model

  Think of it like this:

  - entity = how backend stores data
  - DTO = what backend chooses to show outside

  Those are often similar in small projects, but they should not be treated as the same thing.

  ———

  ## Short answer

  ### Why stream and map?

  Because you are converting:

  - Folder -> FolderResponse
  - FileRecord -> FileSummary

  not because the repo failed to return a list.

  ### Why DTO instead of returning entity?

  Because API contract and database model should stay decoupled, even if they look the same right now.

  ———

  */
