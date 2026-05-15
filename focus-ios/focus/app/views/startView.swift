//
//  startView.swift
//  focus
//
//  Created by 이동언 on 3/27/26.
//

import SwiftUI

struct StartView: View {
    let onTapPrepare: () -> Void

    var body: some View {
        LaunchIntroView(onTapContinue: onTapPrepare)
    }
}

#Preview {
    StartView(onTapPrepare: {})
}
