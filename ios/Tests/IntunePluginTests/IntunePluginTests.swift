import XCTest
import Capacitor
@testable import IntunePlugin

class IntuneTests: XCTestCase {
    private let settingsMessage = "IntuneMAMSettings must be set in Info.plist to use this method. See https://docs.microsoft.com/en-us/mem/intune/developer/app-sdk-ios#configure-msal-settings-for-the-intune-app-sdk"

    func testPluginIsRegisteredUnderItsJavaScriptName() {
        let plugin = IntuneMAM()

        XCTAssertEqual(plugin.identifier, "IntuneMAM")
        XCTAssertEqual(plugin.jsName, "IntuneMAM")
    }

    func testPluginExposesItsMethodsAsPromises() {
        let plugin = IntuneMAM()

        XCTAssertEqual(plugin.pluginMethods.map(\.name), [
            "loginAndEnrollAccount",
            "acquireToken",
            "acquireTokenSilent",
            "registerAndEnrollAccount",
            "enrolledAccount",
            "deRegisterAndUnenrollAccount",
            "logoutOfAccount",
            "getPolicy",
            "groupName",
            "appConfig",
            "sdkVersion",
            "displayDiagnosticConsole"
        ])
        XCTAssertTrue(plugin.pluginMethods.allSatisfy { $0.returnType == .promise })
    }

    func testMissingArgumentsThrowWithTheSameMessagesAndNoCode() {
        let plugin = IntuneMAM()
        let cases: [Rejection] = [
            Rejection("acquireToken", plugin.acquireToken, [:], "scopes not provided"),
            Rejection("acquireTokenSilent", plugin.acquireTokenSilent, ["scopes": ["openid"]], "accountId must be provided to refresh token"),
            Rejection("acquireTokenSilent", plugin.acquireTokenSilent, ["accountId": "id"], "scopes not provided"),
            Rejection("registerAndEnrollAccount", plugin.registerAndEnrollAccount, [:], "accountId must be provided. Call acquireToken first"),
            Rejection("deRegisterAndUnenrollAccount", plugin.deRegisterAndUnenrollAccount, [:], "No accountId provided"),
            Rejection("logoutOfAccount", plugin.logoutOfAccount, [:], "No accountId provided"),
            Rejection("getPolicy", plugin.getPolicy, [:], "No accountId provided"),
            Rejection("groupName", plugin.groupName, [:], "No accountId provided"),
            Rejection("appConfig", plugin.appConfig, [:], "No accountId provided")
        ]
        for item in cases {
            assertThrows(item)
        }
    }

    func testMethodsThatNeedMSALThrowWithoutTheIntuneSettingsInInfoPlist() {
        // The test bundle's main bundle has no IntuneMAMSettings.
        let plugin = IntuneMAM()
        let cases: [Rejection] = [
            Rejection("acquireToken", plugin.acquireToken, ["scopes": ["openid"]], settingsMessage),
            Rejection("acquireTokenSilent", plugin.acquireTokenSilent, ["accountId": "id", "scopes": ["openid"]], settingsMessage),
            Rejection("deRegisterAndUnenrollAccount", plugin.deRegisterAndUnenrollAccount, ["accountId": "id"], settingsMessage),
            Rejection("logoutOfAccount", plugin.logoutOfAccount, ["accountId": "id"], settingsMessage)
        ]
        for item in cases {
            assertThrows(item)
        }
    }

    private func assertThrows(_ item: Rejection, file: StaticString = #filePath, line: UInt = #line) {
        let name = item.name
        let call = CAPPluginCall(callbackId: "test", methodName: name, options: item.options, success: { _, _ in
            XCTFail("\(name) must not resolve", file: file, line: line)
        }, error: { _ in
            XCTFail("\(name) answers by throwing", file: file, line: line)
        })
        XCTAssertThrowsError(try item.method(call), name, file: file, line: line) { error in
            XCTAssertEqual((error as? CAPPluginError)?.message, item.message, name, file: file, line: line)
            XCTAssertNil((error as? CAPPluginError)?.code, name, file: file, line: line)
        }
    }
}

/// A method called with options it rejects, and the message it rejects them with.
private struct Rejection {
    let name: String
    let method: (CAPPluginCall) throws -> Void
    let options: JSObject
    let message: String

    init(_ name: String, _ method: @escaping (CAPPluginCall) throws -> Void, _ options: JSObject, _ message: String) {
        self.name = name
        self.method = method
        self.options = options
        self.message = message
    }
}
