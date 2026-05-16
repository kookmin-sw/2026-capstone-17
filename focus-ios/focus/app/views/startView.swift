//
//  startView.swift
//  focus
//
//  Created by 이동언 on 3/27/26.
//

import SwiftUI

struct StartView: View {
    let onTapPrepare: () -> Void
    let isLoading: Bool
    let errorMessage: String?

    var body: some View {
        LaunchIntroView(
            onTapContinue: onTapPrepare,
            isLoading: isLoading,
            errorMessage: errorMessage
        )
    }
}

#Preview {
    StartView(onTapPrepare: {}, isLoading: false, errorMessage: nil)
}
