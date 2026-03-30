package com.example.dropbox.metadata.folders;

import com.example.dropbox.metadata.common.ForbiddenOperationException;
import com.example.dropbox.metadata.common.ResourceNotFoundException;
import com.example.dropbox.metadata.files.FileRecord;
import com.example.dropbox.metadata.files.FileRecordRepository;
import com.example.dropbox.metadata.files.FileSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.dropbox.metadata.shares.PermissionService;


@Service
@RequiredArgsConstructor
public class FolderService {
    private final FolderRepository folderRepository;
    private final FileRecordRepository fileRecordRepository;
    private final PermissionService permissionService;

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
                                        .map(this::toFolderResponse)
                                        .toList();

        List<FileSummary> files = fileRecordRepository.findByFolderId(folderId)
                                        .stream()
                                        .map(this::toFileSummary)
                                        .toList();
        return new FolderChildrenResponse(folders, files);
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
