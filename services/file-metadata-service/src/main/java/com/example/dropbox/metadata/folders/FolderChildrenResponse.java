package com.example.dropbox.metadata.folders;

import com.example.dropbox.metadata.files.FileSummary;
import java.util.List;

public record FolderChildrenResponse(
        List<FolderResponse> folders,
        List<FileSummary> files
) {
}
