//
//  videoSampleBufferFactory.swift
//  focus
//
//  Created by Codex on 5/16/26.
//

import AVFoundation
import CoreMedia
import CoreVideo

enum VideoSampleBufferFactory {
    static func makeSampleBuffer(
        from pixelBuffer: CVPixelBuffer,
        timingSource: CMSampleBuffer
    ) -> CMSampleBuffer? {
        var formatDescription: CMVideoFormatDescription?
        let formatStatus = CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescriptionOut: &formatDescription
        )

        guard formatStatus == noErr,
              let formatDescription else {
            return nil
        }

        var timing = CMSampleTimingInfo(
            duration: CMSampleBufferGetDuration(timingSource),
            presentationTimeStamp: CMSampleBufferGetPresentationTimeStamp(timingSource),
            decodeTimeStamp: CMSampleBufferGetDecodeTimeStamp(timingSource)
        )

        var sampleBuffer: CMSampleBuffer?
        let sampleStatus = CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescription: formatDescription,
            sampleTiming: &timing,
            sampleBufferOut: &sampleBuffer
        )

        guard sampleStatus == noErr,
              let sampleBuffer else {
            return nil
        }

        copySampleAttachments(from: timingSource, to: sampleBuffer)
        return sampleBuffer
    }

    private static func copySampleAttachments(
        from source: CMSampleBuffer,
        to destination: CMSampleBuffer
    ) {
        guard let sourceAttachments = CMSampleBufferGetSampleAttachmentsArray(
            source,
            createIfNecessary: false
        ) as? [CFDictionary],
        let first = sourceAttachments.first else {
            return
        }

        guard let destinationAttachments = CMSampleBufferGetSampleAttachmentsArray(
            destination,
            createIfNecessary: true
        ) as? NSMutableArray,
        let destinationFirst = destinationAttachments.firstObject else {
            return
        }

        let mutable = unsafeBitCast(destinationFirst, to: CFMutableDictionary.self)
        CFDictionaryRemoveAllValues(mutable)

        let sourceDict = unsafeBitCast(first, to: NSDictionary.self)
        for (key, value) in sourceDict {
            CFDictionarySetValue(
                mutable,
                Unmanaged.passUnretained(key as AnyObject).toOpaque(),
                Unmanaged.passUnretained(value as AnyObject).toOpaque()
            )
        }
    }
}
