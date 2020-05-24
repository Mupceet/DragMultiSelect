# DragMultiSelect

[![Commitizen friendly](https://img.shields.io/badge/commitizen-friendly-brightgreen.svg)](http://commitizen.github.io/cz-cli/)

一般的，RecyclerView 的选择流程为：

- 长按时进入选择模式
- 进行选择
- 对选择项进行操作（如删除、分享），然后退出选择模式；或者返回键退出选择模式

第一和第三步不用多说，此库是为了让 RecyclerView 的选择操作更加流畅好用，给其增加了滑动选择功能。与常见的长按 **拖动选择** 功能相比，此库给列表型列表增加了一个 **滑动选择** 功能，即可以在指定的滑动区域内滑动时进行选择。

首先从选择的效果说起。

## 选择效果

选择效果的四种模式分别是：

- SelectAndKeep：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时保持选中。
- SelectAndReverse：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时取消选中。
- SelectAndUndo：选中第一个 Item，其他 Item 在滑过时被选中，往回滑时恢复原状态。
- ToggleAndKeep：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时保持状态不变。
- ToggleAndReverse：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时与第一个 Item 的状态相反。
- ToggleAndUndo：反选第一个 Item，其他 Item 在滑过时与第一个 Item 的状态保持一致，往回滑时恢复该 Item 的原状态。

这些模式的效果请看 GIF 图：

TODO： 待补充效果图

接下来是使用步骤。

## Step 1 of 3: 创建选择时的回调

要正常地使用此功能，必须创建、设置选择时的回调。

### Callback: Simple 模式

如果只需要实现 Simple 模式的话，创建简单的 `DragSelectTouchHelper.Callback` 即可。

```Java
private DragSelectTouchHelper.Callback mDragSelectTouchHelperCallback =
    new DragSelectTouchHelper.Callback() {
        @Override
        public void onSelectChange(int position, boolean newState) {
            // 更新该条目的状态
            mAdapter.select(position, newState);
        }
    };
```

### Callback: Simple 模式增强

如果需要在 Simple 模式基础上，在选择开始与结束的时候进行回调，重写父类的方法即可。

```Java
private DragSelectTouchHelper.Callback mDragSelectTouchHelperCallback =
    new DragSelectTouchHelper.Callback() {
        @Override
        public void onSelectChange(int position, boolean newState) {
            // 更新该条目的状态
            mAdapter.select(position, newState);
        }

        @Override
        public void onSelectStart(int start) {
            super.onSelectStart(start);
            // 选择从此开始
        }

        @Override
        public void onSelectEnd(int end) {
            super.onSelectEnd(end);
            // 选择在此结束
        }
    };
```

### AdvanceCallback

不失一般的，为了实现多种模式，需要创建 `DragSelectTouchHelper.AdvanceCallback`。

```Java
private DragSelectTouchHelper.Callback mDragSelectTouchHelperCallback =
    new AdvanceCallback(AdvanceCallback.Mode.FirstItemDependent) {

        @Override
        public HashSet<Integer> currentSelectedId() {
            // 点击开始时获取已选择项
            return mAdapter.currentSelectedId();
        }

        @Override
        public void updateSelectState(int position, boolean newState) {
            // 更新该条目的状态
            mAdapter.select(position, newState);
        }
    };
```

同样的选择开始与结束的时候进行回调，重写父类的方法即可。

## Step 2 of 3: 创建 DragSelectTouchHelper

```Java
mDragSelectTouchHelper = new DragSelectTouchHelper(mDragSelectTouchHelperCallback);
```

以下为 DragSelectTouchHelper 可以配置的选项。

```Java
mDragSelectTouchHelper
        .setHotspotRatio(0.2f) // 默认滚动区高度为列表的 1/5
        .setHotspotOffset(0) // 设置滚动区离控件的距离，默认为 0
        .setMaximumVelocity(9) // 默认滚动最大速度为 9
        .setEdgeType(DragSelectTouchHelper.EDGE_TYPE_INSIDE_EXTEND) // 设置是否允许在列表之外继续滚动，默认为允许
        .setAutoEnterSlideMode(false) // 设置自动进入滑动选择模式，默认为不允许
        .setAllowDragInSlideMode(false) // 设置在滑动选择模式下允许长按拖动选择，默认为不允许
        .setSlideArea(0, 0); // 滑动选择模式下指定的滑动区域 start~end
```

>**Note:** 注意，只需要配置你所需要的，以下是一些例子

### 列表型

#### 长按启动选择监听，可拖动选择；抬手关闭选择监听

```Java
//全部不需要设置
```

#### 长按启动选择监听，可拖动选择；抬手转换监听模式，可滑动选择；需要主动关闭选择监听

在抬手时，自动转换为滑动选择模式，可配置滑动选择模式下是否还能长按拖动选择。

```Java
// 注意：必须设置 setAutoEnterSlideMode() 与 setSlideArea()，可选择配置 setAllowDragInSlideMode()
mDragSelectTouchHelper.setAutoEnterSlideMode(true) // 设置自动进入滑动选择模式，默认为不允许
        .setAllowDragInSlideMode(false) // 设置在滑动选择模式下允许长按拖动选择，默认为不允许
        .setSlideArea(0, 64); // 滑动选择模式下指定的滑动区域 start~end
```

在想退出选择功能时，需要调用此方法以关闭选择监听：

```Java
mDragSelectTouchHelper.inactiveSelect();
```

#### 直接启动选择监听

若列表直接处于选择模式，可以直接启动选择监听，此时在 **滑动区域内滑动可选择**，还可配置是否允许 **长按时可拖动选择**，默认为不允许，此种行为下，也需要主动在退出时关闭选择监听。

```Java
// 注意：必须设置 setSlideArea()
mDragSelectTouchHelper.setSlideArea(0, 64); // 滑动选择模式下指定的滑动区域 start~end
// .setAllowDragInSlideMode(false) // 设置在滑动选择模式下允许长按拖动选择，默认为不允许

// 直接开启选择监听
mDragSelectTouchHelper.activeSlideSelect();
```

在想退出选择功能时，调用此方法以关闭选择监听：

```Java
mDragSelectTouchHelper.inactiveSelect();
```

### 网格型

- 长按启动选择监听，可拖动选择；抬手关闭选择监听
- 点击 CheckBox 时启动选择监听，可拖动选择；抬手关闭选择监听

仅在此说明第二种操作：

滑动选择的监听的方式是针对列表型的，很容易理解，因为列表型在滑动多选模式下，要指定滑动选择的区域，而这个区域通常限制在 CheckBox 所在的位置，是很有限的宽度，所以列表的滚动与滑动选择是不冲突的。而网格型列表要是使用没去选择的功能就无法滚动列表了，其实换一个思路就会发现有一种骚操作……

正常情况下，长按时才启动列表的选择模式，并且在此时开启选择监听，在拖动多选结束后，退出了选择监听（这是设计的初衷）。实际上，你可以任意开启选择监听进行选择，所以……在点击到 CheckBox 时，同样可以启动选择监听！所以支持以下写法：

```Java
// 需要自行实现此操作
private void onCheckBoxClick(int position) {
    mDragSelectTouchHelper.activeDragSelect(position);
}
```

本质上来说网格型的滑动选择功能的配置方式是相同的，只要不局限在长按时才开启即可。

## Step 3 of 3: RecyclerView 关联 DragSelectTouchHelper

将上面创建好的 DragSelectTouchHelper 与 RecyclerView 关联：

```Java
mDragSelectTouchHelper.attachToRecyclerView(mRecyclerView);
```

长按时调用 `mDragSelectTouchHelper.activeDragSelect(position)` 即可进行拖动多选，又或者在合适的时候，直接就让列表处于选择模式（上文提到了两种方式，具体查看源码）：

```Java
mDragSelectTouchListener.activeSlideSelect();
```

## 致谢

此库参考以下三个拖动多选的库，基于实际项目进行改动，修复一些 Bug。而且参考 Android 源码中的 ItemTouchHelper 类对代码进行封装。

- [afollestad/drag-select-recyclerview](https://github.com/afollestad/drag-select-recyclerview)
- [weidongjian/AndroidDragSelect-SimulateGooglePhoto](https://github.com/weidongjian/AndroidDragSelect-SimulateGooglePhoto)
- [MFlisar/DragSelectRecyclerView](https://github.com/MFlisar/DragSelectRecyclerView)

## License

```
Copyright 2020 Mupceet

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
