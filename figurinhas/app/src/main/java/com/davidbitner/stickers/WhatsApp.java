/*
 * Copyright (c) WhatsApp Inc. and its affiliates. All rights reserved.
 *
 * Derived from the WhatsApp sticker sample app. Distributed under the BSD license
 * found in LICENSE-WhatsApp-Stickers.txt.
 */

package com.davidbitner.stickers;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

final class WhatsApp {
    static final int REQUEST_ADD = 200;

    private static final String CONSUMER = "com.whatsapp";
    private static final String BUSINESS = "com.whatsapp.w4b";
    private static final String WHITELIST_PROVIDER = ".provider.sticker_whitelist_check";

    private WhatsApp() {
    }

    static void addPack(Activity activity, String identifier, String name) {
        try {
            PackageManager pm = activity.getPackageManager();
            if (!isInstalled(CONSUMER, pm) && !isInstalled(BUSINESS, pm)) {
                Toast.makeText(activity, "WhatsApp não encontrado neste aparelho", Toast.LENGTH_LONG).show();
                return;
            }
            boolean inConsumer = isWhitelisted(activity, identifier, CONSUMER);
            boolean inBusiness = isWhitelisted(activity, identifier, BUSINESS);
            if (!inConsumer && !inBusiness) {
                launch(activity, Intent.createChooser(buildIntent(identifier, name), "Adicionar ao WhatsApp"));
            } else if (!inConsumer) {
                Intent intent = buildIntent(identifier, name);
                intent.setPackage(CONSUMER);
                launch(activity, intent);
            } else if (!inBusiness) {
                Intent intent = buildIntent(identifier, name);
                intent.setPackage(BUSINESS);
                launch(activity, intent);
            } else {
                Toast.makeText(activity, "Este pacote já está no WhatsApp", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Não consegui abrir o WhatsApp: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void launch(Activity activity, Intent intent) {
        try {
            activity.startActivityForResult(intent, REQUEST_ADD);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Atualize o WhatsApp para adicionar pacotes de figurinhas", Toast.LENGTH_LONG).show();
        }
    }

    private static Intent buildIntent(String identifier, String name) {
        Intent intent = new Intent();
        intent.setAction("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", identifier);
        intent.putExtra("sticker_pack_authority", BuildConfig.CONTENT_PROVIDER_AUTHORITY);
        intent.putExtra("sticker_pack_name", name);
        return intent;
    }

    private static boolean isInstalled(String packageName, PackageManager pm) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return info != null && info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isWhitelisted(Context context, String identifier, String whatsappPackage) {
        PackageManager pm = context.getPackageManager();
        if (!isInstalled(whatsappPackage, pm)) {
            return true;
        }
        String authority = whatsappPackage + WHITELIST_PROVIDER;
        ProviderInfo providerInfo = pm.resolveContentProvider(authority, PackageManager.GET_META_DATA);
        if (providerInfo == null) {
            return false;
        }
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", BuildConfig.CONTENT_PROVIDER_AUTHORITY)
                .appendQueryParameter("identifier", identifier)
                .build();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow("result")) == 1;
            }
        }
        return false;
    }
}
