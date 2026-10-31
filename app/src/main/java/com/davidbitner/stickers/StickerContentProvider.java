/*
 * Copyright (c) WhatsApp Inc. and its affiliates. All rights reserved.
 *
 * Derived from the WhatsApp sticker sample app. Distributed under the BSD license
 * found in LICENSE-WhatsApp-Stickers.txt.
 */

package com.davidbitner.stickers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StickerContentProvider extends ContentProvider {

    public static final String STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier";
    public static final String STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name";
    public static final String STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher";
    public static final String STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon";
    public static final String ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link";
    public static final String IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link";
    public static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    public static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    public static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
    public static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
    public static final String IMAGE_DATA_VERSION = "image_data_version";
    public static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
    public static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";

    public static final String STICKER_FILE_NAME_IN_QUERY = "sticker_file_name";
    public static final String STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji";
    public static final String STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text";

    private static final String METADATA = "metadata";
    static final String STICKERS = "stickers";
    static final String STICKERS_ASSET = "stickers_asset";

    public static final Uri AUTHORITY_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(BuildConfig.CONTENT_PROVIDER_AUTHORITY)
            .appendPath(METADATA)
            .build();

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int METADATA_CODE = 1;
    private static final int METADATA_CODE_FOR_SINGLE_PACK = 2;
    private static final int STICKERS_CODE = 3;
    private static final int STICKERS_ASSET_CODE = 4;

    @Override
    public boolean onCreate() {
        final String authority = BuildConfig.CONTENT_PROVIDER_AUTHORITY;
        MATCHER.addURI(authority, METADATA, METADATA_CODE);
        MATCHER.addURI(authority, METADATA + "/*", METADATA_CODE_FOR_SINGLE_PACK);
        MATCHER.addURI(authority, STICKERS + "/*", STICKERS_CODE);
        MATCHER.addURI(authority, STICKERS_ASSET + "/*/*", STICKERS_ASSET_CODE);
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final int code = MATCHER.match(uri);
        if (code == METADATA_CODE) {
            List<Pack> ready = new ArrayList<>();
            for (Pack p : PackStore.loadAll(appContext())) {
                if (p.isReady()) {
                    ready.add(p);
                }
            }
            return getStickerPackInfo(uri, ready);
        } else if (code == METADATA_CODE_FOR_SINGLE_PACK) {
            List<Pack> single = new ArrayList<>();
            Pack p = PackStore.load(appContext(), uri.getLastPathSegment());
            if (p != null) {
                single.add(p);
            }
            return getStickerPackInfo(uri, single);
        } else if (code == STICKERS_CODE) {
            return getStickersForAStickerPack(uri);
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (MATCHER.match(uri) != STICKERS_ASSET_CODE) {
            return null;
        }
        final List<String> segments = uri.getPathSegments();
        if (segments.size() != 3) {
            throw new IllegalArgumentException("path segments should be 3, uri is: " + uri);
        }
        final String identifier = segments.get(1);
        final String fileName = segments.get(2);
        if (TextUtils.isEmpty(identifier) || TextUtils.isEmpty(fileName)
                || identifier.contains("..") || identifier.contains("/")
                || fileName.contains("..") || fileName.contains("/")) {
            throw new IllegalArgumentException("invalid uri: " + uri);
        }
        if (!fileName.endsWith(".webp") && !fileName.equals(Pack.TRAY_FILE)) {
            throw new FileNotFoundException(fileName);
        }
        final File file = new File(PackStore.dir(appContext(), identifier), fileName);
        if (!file.isFile()) {
            throw new FileNotFoundException(file.getName());
        }
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }

    @Override
    public String getType(@NonNull Uri uri) {
        final int code = MATCHER.match(uri);
        switch (code) {
            case METADATA_CODE:
                return "vnd.android.cursor.dir/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + METADATA;
            case METADATA_CODE_FOR_SINGLE_PACK:
                return "vnd.android.cursor.item/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + METADATA;
            case STICKERS_CODE:
                return "vnd.android.cursor.dir/vnd." + BuildConfig.CONTENT_PROVIDER_AUTHORITY + "." + STICKERS;
            case STICKERS_ASSET_CODE:
                final String last = uri.getLastPathSegment();
                return last != null && last.endsWith(".png") ? "image/png" : "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @NonNull
    private Cursor getStickerPackInfo(@NonNull Uri uri, @NonNull List<Pack> packs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
        });
        for (Pack p : packs) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            row.add(p.id);
            row.add(p.name);
            row.add(PackStore.PUBLISHER);
            row.add(Pack.TRAY_FILE);
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(String.valueOf(p.version));
            row.add(0);
            row.add(p.animated ? 1 : 0);
        }
        cursor.setNotificationUri(appContext().getContentResolver(), uri);
        return cursor;
    }

    @NonNull
    private Cursor getStickersForAStickerPack(@NonNull Uri uri) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                STICKER_FILE_NAME_IN_QUERY,
                STICKER_FILE_EMOJI_IN_QUERY,
                STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY});
        Pack p = PackStore.load(appContext(), uri.getLastPathSegment());
        if (p != null) {
            for (Pack.Item it : p.items) {
                cursor.addRow(new Object[]{it.file, it.emoji, ""});
            }
        }
        cursor.setNotificationUri(appContext().getContentResolver(), uri);
        return cursor;
    }

    @NonNull
    private Context appContext() {
        return Objects.requireNonNull(getContext());
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }
}
