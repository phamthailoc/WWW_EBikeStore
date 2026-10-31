import { useEffect } from "react";
import { bootstrapSessionThunk, useAppDispatch } from "@ebike/shared-code/redux";

const AuthBootstrap = () => {
  const dispatch = useAppDispatch();

  useEffect(() => {
    void dispatch(bootstrapSessionThunk());
  }, [dispatch]);

  return null;
};

export default AuthBootstrap;
