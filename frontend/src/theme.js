import { theme } from "antd";

// Keep in sync with the CSS variables at the top of styles.css
export const appTheme = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: "#8b5cf6",
    colorInfo: "#8b5cf6",
    colorBgBase: "#0c0c10",
    colorBgLayout: "#0c0c10",
    colorBgContainer: "#15151b",
    colorBgElevated: "#1c1c24",
    colorBorder: "#2a2a34",
    colorBorderSecondary: "#202029",
    colorTextBase: "#ececf1",
    borderRadius: 10,
  },
  components: {
    Layout: {
      headerBg: "rgba(12, 12, 16, 0.85)",
      headerHeight: 64,
      headerPadding: "0 20px",
      siderBg: "#0c0c10",
      bodyBg: "#0c0c10",
    },
    Segmented: {
      trackBg: "#15151b",
      itemSelectedBg: "#2a2a34",
    },
  },
};
