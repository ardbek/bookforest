package com.ebook.bookforest.domain.user.dto;

import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;

public record FavoriteLibraryResponse(
    Long libraryId,
    String libraryName,
    String platformCode,
    String platformName,
    String siteUrl
) {

    public static FavoriteLibraryResponse from(FavoriteLibrary favoriteLibrary) {
        Library library = favoriteLibrary.getLibrary();
        Platform platform = library.getPlatform();

        return new FavoriteLibraryResponse(
            library.getId(),
            library.getLibraryName(),
            platform.getPlatformCode(),
            platform.getPlatformName(),
            library.getSiteUrl()
        );
    }
}
