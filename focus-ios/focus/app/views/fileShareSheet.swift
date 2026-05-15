//
//  fileShareSheet.swift
//  focus
//
//  Created by Codex on 5/15/26.
//

import SwiftUI
import UIKit

struct FileShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
