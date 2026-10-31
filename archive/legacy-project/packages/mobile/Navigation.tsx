import { Text, View, StyleSheet } from "react-native";

const Navigation = () => (
  <View style={styles.container}>
    <Text style={styles.title}>E-Bike Mobile</Text>
    <Text>Navigation scaffold for React Native screens.</Text>
  </View>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 24
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 12
  }
});

export default Navigation;
