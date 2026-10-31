import { Provider } from "react-redux";
import { SafeAreaView, StatusBar, StyleSheet } from "react-native";
import { createAppStore } from "@ebike/shared-code/redux";
import Navigation from "./Navigation";

const store = createAppStore();

const App = () => (
  <Provider store={store}>
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <Navigation />
    </SafeAreaView>
  </Provider>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f4f7fb"
  }
});

export default App;
