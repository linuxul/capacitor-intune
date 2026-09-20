import XCTest
@testable import IntunePlugin

class IntuneTests: XCTestCase {
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
}
