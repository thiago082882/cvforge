import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        let url = Bundle.main.object(forInfoDictionaryKey: "CVForgeApiUrl") as? String ?? "http://localhost:8080"
        IosKt.initKoinIos(apiBaseUrl: url)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea(.keyboard)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
