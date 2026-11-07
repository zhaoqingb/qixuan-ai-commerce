import axios from "axios"


// 创建统一的Axios实例
const http = axios.create({
    baseURL: "/api",
    timeout:10000,
})

export default http