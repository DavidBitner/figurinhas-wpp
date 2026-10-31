package com.davidbitner.stickers;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import java.util.concurrent.TimeUnit;

final class BackgroundRemover {
    private static SubjectSegmenter segmenter;

    private BackgroundRemover() {
    }

    static synchronized Bitmap remove(Bitmap source) throws Exception {
        if (segmenter == null) {
            segmenter = SubjectSegmentation.getClient(
                    new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
        }
        SubjectSegmentationResult result = Tasks.await(
                segmenter.process(InputImage.fromBitmap(source, 0)), 90, TimeUnit.SECONDS);
        Bitmap foreground = result.getForegroundBitmap();
        if (foreground == null) {
            throw new IllegalStateException("Sem resultado da segmentação");
        }
        return foreground;
    }
}
