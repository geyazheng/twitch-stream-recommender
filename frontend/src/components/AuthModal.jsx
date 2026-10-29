import React, { useState } from "react";
import { App, Button, Form, Input, Modal } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import Logo from "./Logo";
import { login, register } from "../api";

const COPY = {
  login: {
    title: "Welcome back",
    intro: "Log in to get recommendations from the games you favorite.",
    submit: "Log in",
    switchPrompt: "New here?",
    switchAction: "Create an account",
  },
  register: {
    title: "Create your account",
    intro: "Save streams, videos and clips, and get picks based on them.",
    submit: "Create account",
    switchPrompt: "Already have an account?",
    switchAction: "Log in",
  },
};

function AuthModal({ open, mode, notice, onModeChange, onClose, onAuthenticated }) {
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[mode];

  const onFinish = async (values) => {
    setSubmitting(true);
    try {
      if (mode === "register") {
        await register(values);
      }
      await login(values);
      onAuthenticated(values.username);
    } catch (err) {
      message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onCancel={onClose} footer={null} width={420} centered destroyOnHidden>
      <div className="auth-head">
        <Logo size={40} />
        <h2>{copy.title}</h2>
        <p>{notice ?? copy.intro}</p>
      </div>

      <Form key={mode} layout="vertical" requiredMark={false} onFinish={onFinish}>
        <Form.Item name="username" label="Username" rules={[{ required: true, message: "Enter your username" }]}>
          <Input prefix={<UserOutlined />} autoComplete="username" autoFocus />
        </Form.Item>
        <Form.Item
          name="password"
          label="Password"
          rules={[
            { required: true, message: "Enter your password" },
            ...(mode === "register" ? [{ min: 8, message: "Use at least 8 characters" }] : []),
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            autoComplete={mode === "register" ? "new-password" : "current-password"}
          />
        </Form.Item>
        {mode === "register" && (
          <div className="name-row">
            <Form.Item name="first_name" label="First name" rules={[{ required: true, message: "Required" }]}>
              <Input autoComplete="given-name" />
            </Form.Item>
            <Form.Item name="last_name" label="Last name" rules={[{ required: true, message: "Required" }]}>
              <Input autoComplete="family-name" />
            </Form.Item>
          </div>
        )}
        <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
          {copy.submit}
        </Button>
      </Form>

      <p className="auth-switch">
        {copy.switchPrompt}
        <Button type="link" onClick={() => onModeChange(mode === "login" ? "register" : "login")}>
          {copy.switchAction}
        </Button>
      </p>
    </Modal>
  );
}

export default AuthModal;
