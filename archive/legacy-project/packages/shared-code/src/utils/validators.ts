export const validateEmail = (email: string) => /\S+@\S+\.\S+/.test(email);
export const validatePassword = (password: string) => password.length >= 8;
