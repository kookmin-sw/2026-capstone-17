//
//  openCVYuNetWrapper.mm
//  focus
//
//  Created by 이동언 on 3/9/26.
//

#import "OpenCVYuNetWrapper.h"
#import <opencv2/objdetect/face.hpp>
#import <opencv2/imgproc.hpp>

using namespace cv;
using namespace std;

@interface OpenCVYuNetWrapper () {
    cv::Ptr<cv::FaceDetectorYN> _detector;
    int _inputSize;
}
@end

@implementation OpenCVYuNetWrapper

- (nullable instancetype)initWithModelPath:(NSString *)modelPath
                                 inputSize:(int)inputSize
                            scoreThreshold:(float)scoreThreshold
                              nmsThreshold:(float)nmsThreshold
                                      topK:(int)topK {
    self = [super init];
    if (self) {
        _inputSize = inputSize;

        std::string model = [modelPath UTF8String];
        _detector = cv::FaceDetectorYN::create(
            model,
            "",
            cv::Size(inputSize, inputSize),
            scoreThreshold,
            nmsThreshold,
            topK
        );

        if (_detector.empty()) {
            return nil;
        }
    }
    return self;
}

- (NSArray<NSDictionary *> *)detectFacesInPixelBuffer:(CVPixelBufferRef)pixelBuffer {
    if (!_detector || pixelBuffer == nil) {
        return @[];
    }

    CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly);

    size_t width = CVPixelBufferGetWidth(pixelBuffer);
    size_t height = CVPixelBufferGetHeight(pixelBuffer);
    OSType format = CVPixelBufferGetPixelFormatType(pixelBuffer);

    if (format != kCVPixelFormatType_32BGRA) {
        CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly);
        return @[];
    }

    void *baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer);
    size_t bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer);

    cv::Mat bgra((int)height, (int)width, CV_8UC4, baseAddress, bytesPerRow);
    cv::Mat bgr;
    cv::cvtColor(bgra, bgr, cv::COLOR_BGRA2BGR);

    CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly);

    int originalWidth = (int)width;
    int originalHeight = (int)height;
    int shortSide = std::max(1, std::min(originalWidth, originalHeight));
    double scale = (double)_inputSize / (double)shortSide;

    int targetW = std::max(1, (int)round((double)originalWidth * scale));
    int targetH = std::max(1, (int)round((double)originalHeight * scale));

    cv::Mat small;
    cv::resize(bgr, small, cv::Size(targetW, targetH));

    _detector->setInputSize(cv::Size(targetW, targetH));

    cv::Mat faces;
    _detector->detect(small, faces);

    if (faces.empty()) {
        return @[];
    }

    float scaleX = (float)width / (float)targetW;
    float scaleY = (float)height / (float)targetH;

    NSMutableArray<NSDictionary *> *results = [NSMutableArray array];

    for (int i = 0; i < faces.rows; i++) {
        const float *row = faces.ptr<float>(i);

        float x = row[0] * scaleX;
        float y = row[1] * scaleY;
        float w = row[2] * scaleX;
        float h = row[3] * scaleY;

        float re_x = row[4] * scaleX;
        float re_y = row[5] * scaleY;
        float le_x = row[6] * scaleX;
        float le_y = row[7] * scaleY;
        float nose_x = row[8] * scaleX;
        float nose_y = row[9] * scaleY;
        float rm_x = row[10] * scaleX;
        float rm_y = row[11] * scaleY;
        float lm_x = row[12] * scaleX;
        float lm_y = row[13] * scaleY;
        float score = row[14];

        float clampedX = std::max(0.0f, std::min(x, (float)width - 1.0f));
        float clampedY = std::max(0.0f, std::min(y, (float)height - 1.0f));
        float clampedW = std::max(1.0f, std::min(w, (float)width - clampedX));
        float clampedH = std::max(1.0f, std::min(h, (float)height - clampedY));

        NSDictionary *item = @{
            @"x": @(clampedX),
            @"y": @(clampedY),
            @"width": @(clampedW),
            @"height": @(clampedH),
            @"rightEyeX": @(re_x),
            @"rightEyeY": @(re_y),
            @"leftEyeX": @(le_x),
            @"leftEyeY": @(le_y),
            @"noseX": @(nose_x),
            @"noseY": @(nose_y),
            @"rightMouthX": @(rm_x),
            @"rightMouthY": @(rm_y),
            @"leftMouthX": @(lm_x),
            @"leftMouthY": @(lm_y),
            @"score": @(score)
        };

        [results addObject:item];
    }

    return results;
}

@end
