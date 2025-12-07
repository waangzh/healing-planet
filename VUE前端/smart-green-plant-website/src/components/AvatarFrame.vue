<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router'
import { User, SwitchButton } from '@element-plus/icons-vue';
import avatarSvg from '@/assets/默认头像.svg';
import { useUserStore } from '@/stores';

const userStore = useUserStore();
const router = useRouter();

const onCommand = async (command) => {
    if (command === 'logout') {
        await ElMessageBox.confirm('你确认退出植愈星球系统吗？', '温馨提示', {
            type: 'warning',
            confirmButtonText: '确认',
            cancelButtonText: '取消'
        });
        userStore.removeToken();
        userStore.setUser({});
        router.push(`/login`);
    } else {
        router.push(`/${command}`);
    }
};

const dropdownVisible = ref(false);

const handleDropdownShow = () => {
    dropdownVisible.value = true;
};

const handleDropdownHide = () => {
    dropdownVisible.value = false;
};

const avatar = userStore.user.avatar || avatarSvg;
</script>

<template>
    <div class="avatar">
        <el-dropdown placement="bottom" @command="onCommand"
            @visible-change="(visible) => { if (visible) handleDropdownShow(); else handleDropdownHide(); }">
            <span class="el-dropdown__box">
                <el-avatar :src="avatar" :class="{ 'avatar-hover-effect': dropdownVisible }" />
            </span>

            <template #dropdown>
                <el-dropdown-menu class="custom-dropdown-menu">
                    <div class="dropdown-header">
                        欢迎您，{{
                            userStore.user.nickName || "游客" }}</div>
                    <el-dropdown-item command="user" :icon="User">个人信息</el-dropdown-item>
                    <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
</template>

<style lang="scss" scoped>
.avatar {
    width: 100px;
    display: flex;
    justify-content: center;
}

.el-dropdown__box {
    position: relative;
    display: flex;
    align-items: center;

    .el-avatar {
        background-color: #fff;
        transition: transform 0.3s ease, box-shadow 0.3s ease;
        z-index: 9999;
    }

    .el-avatar.avatar-hover-effect {
        transform: translate(-10px, 25px) scale(1.7);
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
    }

    .el-icon {
        color: #999;
        margin-left: 10px;
    }

    &:hover,
    &:active,
    &:focus {
        outline: none;
    }
}

.custom-dropdown-menu {
    padding: 20px 0;
    width: 160px;
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
    background-color: var(--el-color-primary-light-10);

    .dropdown-header {
        text-align: center;
        padding: 15px 0 5px 0;
        font-weight: 450;
        font-size: 17px;
    }

    .el-dropdown-menu__item {
        padding: 15px 30px;
        font-size: 16px;

        &:hover {
            background-color: #f0f0f0;
        }
    }
}
</style>
