//
//  OpenCVYuNetWrapper.h
//  focus
//
//  Created by 이동언 on 3/9/26.
//


#import <Foundation/Foundation.h>
#import <CoreVideo/CoreVideo.h>

NS_ASSUME_NONNULL_BEGIN

@interface OpenCVYuNetWrapper : NSObject

- (nullable instancetype)initWithModelPath:(NSString *)modelPath
                                 inputSize:(int)inputSize
                            scoreThreshold:(float)scoreThreshold
                              nmsThreshold:(float)nmsThreshold
                                      topK:(int)topK;

- (NSArray<NSDictionary *> *)detectFacesInPixelBuffer:(CVPixelBufferRef)pixelBuffer;

@end

NS_ASSUME_NONNULL_END