import UIKit
import AVFAudio
import shared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback, options: [])
        try? session.setActive(true)
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        if let url = connectionOptions.urlContexts.first?.url {
            MainViewControllerKt.handleIncomingUrl(url: url.absoluteString)
        }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = CoboRootViewController()
        self.window = window
        window.makeKeyAndVisible()
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        if let url = URLContexts.first?.url {
            MainViewControllerKt.handleIncomingUrl(url: url.absoluteString)
        }
    }
}

final class CoboRootViewController: UIViewController {
    private let composeVC = MainViewControllerKt.MainViewController()
    private var orientationMask: UIInterfaceOrientationMask = .portrait
    private var systemBarsVisible: Bool = true
    private var statusBarDarkIcons: Bool? = nil

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(composeVC)
        composeVC.view.frame = view.bounds
        composeVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(composeVC.view)
        composeVC.didMove(toParent: self)

        let nc = NotificationCenter.default
        nc.addObserver(
            self,
            selector: #selector(handleOrientationChanged(_:)),
            name: Notification.Name("CoboOrientationChanged"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleSystemBarsChanged(_:)),
            name: Notification.Name("CoboSystemBarsChanged"),
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(handleStatusBarStyleChanged(_:)),
            name: Notification.Name("CoboStatusBarStyleChanged"),
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        orientationMask
    }

    override var prefersStatusBarHidden: Bool {
        !systemBarsVisible
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        !systemBarsVisible
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        switch statusBarDarkIcons {
        case true: return .darkContent
        case false: return .lightContent
        default: return .default
        }
    }

    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation {
        .fade
    }

    @objc private func handleOrientationChanged(_ note: Notification) {
        guard let rawMask = (note.userInfo?["mask"] as? NSNumber)?.uintValue else { return }
        let nextMask = UIInterfaceOrientationMask(rawValue: rawMask)
        let maskChanged = orientationMask != nextMask
        if maskChanged {
            orientationMask = nextMask
            setNeedsUpdateOfSupportedInterfaceOrientations()
        }
        if let windowScene = view.window?.windowScene {
            let currentBit: UIInterfaceOrientationMask = switch windowScene.interfaceOrientation {
            case .portrait: .portrait
            case .portraitUpsideDown: .portraitUpsideDown
            case .landscapeLeft: .landscapeLeft
            case .landscapeRight: .landscapeRight
            default: []
            }
            if maskChanged || !nextMask.contains(currentBit) {
                windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: nextMask))
            }
        }
    }

    @objc private func handleSystemBarsChanged(_ note: Notification) {
        guard let visible = (note.userInfo?["visible"] as? NSNumber)?.boolValue else { return }
        if systemBarsVisible != visible {
            systemBarsVisible = visible
            UIView.animate(withDuration: 0.2) {
                self.setNeedsStatusBarAppearanceUpdate()
            }
            setNeedsUpdateOfHomeIndicatorAutoHidden()
        }
    }

    @objc private func handleStatusBarStyleChanged(_ note: Notification) {
        guard let raw = (note.userInfo?["darkIcons"] as? NSNumber)?.intValue else { return }
        let next: Bool? = switch raw {
        case 1: true
        case 0: false
        default: nil
        }
        if statusBarDarkIcons != next {
            statusBarDarkIcons = next
            setNeedsStatusBarAppearanceUpdate()
        }
    }
}
